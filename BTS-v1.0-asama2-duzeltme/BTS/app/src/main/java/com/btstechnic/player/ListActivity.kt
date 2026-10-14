package com.btstechnic.player

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Canlı TV / Diziler / Filmler listesi: arama + grup (kategori) çipleri. Bir öğeye dokununca oynatıcı açılır. */
class ListActivity : Activity() {
    private var all = listOf<Entry>(); private var shown = listOf<Entry>()
    private var group = ""; private var query = ""
    private lateinit var adapter: BaseAdapter; private lateinit var count: TextView
    private lateinit var chips: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val kind = Kind.valueOf(intent.getStringExtra("kind") ?: "LIVE")
        all = Library.all.filter { it.kind == kind }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(14), dp(44), dp(14), 0) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "‹"; textSize = 30f; setTextColor(0xFFEEF1FA.toInt()); setPadding(0, 0, dp(14), dp(4)); setOnClickListener { finish() } })
        top.addView(TextView(this).apply { text = when (kind) { Kind.LIVE -> "Canlı TV"; Kind.SERIES -> "Diziler"; else -> "Filmler" }
            textSize = 20f; setTextColor(0xFFEEF1FA.toInt()); setTypeface(typeface, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        count = TextView(this).apply { textSize = 12f; setTextColor(MUTED) }; top.addView(count)
        col.addView(top)
        val search = EditText(this).apply {
            hint = "Kanal, film veya dizi ara"; setHintTextColor(MUTED); setTextColor(0xFFEEF1FA.toInt()); textSize = 14f; setSingleLine()
            background = rounded(CARD, dp(12).toFloat(), 1, 0xFF262E4A.toInt()); setPadding(dp(14), dp(11), dp(14), dp(11))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s.toString().trim(); refresh() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        col.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        chips = LinearLayout(this)
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(6) })
        buildChips()

        val list = ListView(this).apply { divider = android.graphics.drawable.ColorDrawable(0); dividerHeight = dp(6); setSelector(android.R.color.transparent) }
        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, g: ViewGroup?): View {
                val e = shown[p]
                val row = (v as? LinearLayout) ?: LinearLayout(this@ListActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = rounded(CARD, dp(14).toFloat())
                    addView(TextView(context).apply { id = 1; gravity = Gravity.CENTER; textSize = 14f; setTextColor(0xFF04122E.toInt()); setTypeface(typeface, Typeface.BOLD)
                        background = rounded(CYAN, dp(12).toFloat()) }, LinearLayout.LayoutParams(dp(42), dp(42)))
                    addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
                        addView(TextView(context).apply { id = 2; textSize = 14f; setTextColor(0xFFEEF1FA.toInt()); setSingleLine() })
                        addView(TextView(context).apply { id = 3; textSize = 11f; setTextColor(MUTED); setSingleLine() }) }, LinearLayout.LayoutParams(0, -2, 1f))
                    layoutParams = AbsListView.LayoutParams(-1, -2)
                }
                row.findViewById<TextView>(1).text = e.name.trim().take(2).uppercase()
                row.findViewById<TextView>(2).text = e.name
                row.findViewById<TextView>(3).text = e.group.ifEmpty { "—" }
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, p, _ ->
            Playback.queue = shown; Playback.index = p; startActivity(Intent(this, PlayerActivity::class.java))
        }
        col.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(col); refresh()
        if (all.isEmpty()) Toast.makeText(this, "Bu listede içerik yok", Toast.LENGTH_SHORT).show()
    }

    private fun buildChips() {
        val groups = all.map { it.group }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(30).map { it.key }
        fun chip(label: String, g: String) = TextView(this).apply {
            text = label; textSize = 12f; setPadding(dp(14), dp(8), dp(14), dp(8)); setTextColor(0xFFEEF1FA.toInt()); background = rounded(CARD, dp(20).toFloat(), 1, 0xFF262E4A.toInt())
            setOnClickListener { group = g; for (i in 0 until chips.childCount) (chips.getChildAt(i) as TextView).apply {
                val on = tag == g; setTextColor(if (on) 0xFF04122E.toInt() else 0xFFEEF1FA.toInt()); background = if (on) rounded(CYAN, dp(20).toFloat()) else rounded(CARD, dp(20).toFloat(), 1, 0xFF262E4A.toInt()) }; refresh() }
            tag = g
        }
        chips.addView(chip("Tümü", "")); groups.forEach { chips.addView(chip(it, it), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) }) }
        (chips.getChildAt(0) as TextView).performClick()
    }

    private fun refresh() {
        shown = all.filter { (group.isEmpty() || it.group == group) && (query.isEmpty() || it.name.contains(query, true)) }
        count.text = "${shown.size}"; if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }
}
