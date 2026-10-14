package com.btstechnic.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

class HomeActivity : Activity() {
    private lateinit var status: TextView
    private val counts = HashMap<Kind, TextView>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val url = SessionStore.load(this)
        if (url == null) { SessionStore.clear(this); startActivity(Intent(this, LoginActivity::class.java)); finish(); return }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(48), dp(16), dp(16)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(this).apply { setImageResource(R.drawable.app_logo) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        top.addView(TextView(this).apply { text = "BTS Technic"; textSize = 18f; setTextColor(0xFFEEF1FA.toInt()); setTypeface(typeface, Typeface.BOLD); setPadding(dp(10), 0, 0, 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(settingsButton(), LinearLayout.LayoutParams(dp(46), dp(50)))
        col.addView(top)

        val row = LinearLayout(this).apply { weightSum = 3f }
        row.addView(panel("Canlı TV", Kind.LIVE, 0xFF12D8FF.toInt(), 0xFF1B6CFF.toInt()))
        row.addView(panel("Diziler", Kind.SERIES, 0xFFA78BFA.toInt(), 0xFF6D4AFF.toInt()))
        row.addView(panel("Filmler", Kind.MOVIE, 0xFF9CC2FF.toInt(), 0xFF3B82F6.toInt()))
        col.addView(row, LinearLayout.LayoutParams(-1, dp(230)).apply { topMargin = dp(22) })

        status = TextView(this).apply { textSize = 13f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0) }
        col.addView(status)
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(col) })

        if (Library.all.isNotEmpty()) fill() else load(url)
    }

    private fun panel(title: String, k: Kind, c1: Int, c2: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setPadding(dp(6), dp(12), dp(6), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1 and 0x00FFFFFF or 0x55000000, 0xEE0A1236.toInt()))
                .apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), c1 and 0x00FFFFFF or 0x88000000.toInt()) }
            setOnClickListener { startActivity(Intent(this@HomeActivity, ListActivity::class.java).putExtra("kind", k.name)) }
        }
        box.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
        counts[k] = TextView(this).apply { text = "…"; textSize = 11f; setTextColor(0xFFA9BBE8.toInt()); gravity = Gravity.CENTER }
        box.addView(counts[k]!!)
        return box.apply { layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) } }
    }

    /** 3D ayarlar butonu: altta koyu kenar + üstte parlak yüz; basınca aşağı çöker. */
    private fun settingsButton(): View {
        val r = dp(14).toFloat()
        val bg = LayerDrawable(arrayOf(rounded(0xFF0A5A99.toInt(), r), rounded(CYAN, r))).apply { setLayerInset(1, 0, 0, 0, dp(5)) }
        return TextView(this).apply {
            text = "⚙"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(0xFF04122E.toInt()); background = bg; setPadding(0, 0, 0, dp(5))
            setOnTouchListener { v, e ->
                when (e.action) { MotionEvent.ACTION_DOWN -> v.translationY = dp(4).toFloat(); MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.translationY = 0f }
                false
            }
            setOnClickListener {
                AlertDialog.Builder(this@HomeActivity).setTitle("Ayarlar")
                    .setPositiveButton("Oturumu kapat") { _, _ -> SessionStore.clear(this@HomeActivity); startActivity(Intent(this@HomeActivity, LoginActivity::class.java)); finish() }
                    .setNegativeButton("Kapat", null).show()
            }
        }
    }

    private fun load(url: String) {
        status.text = "Liste yükleniyor…"; status.setTextColor(MUTED)
        Executors.newSingleThreadExecutor().execute {
            try { Library.all = M3u.parse(Net.text(url)); runOnUiThread { fill() } }
            catch (e: Exception) {
                runOnUiThread {
                    status.text = "Liste yüklenemedi (${e.message}). Yeniden denemek için dokunun."; status.setTextColor(0xFFFF5D6C.toInt())
                    status.setOnClickListener { status.setOnClickListener(null); load(url) }
                }
            }
        }
    }

    private fun fill() {
        counts[Kind.LIVE]?.text = "${Library.count(Kind.LIVE)} kanal"
        counts[Kind.SERIES]?.text = "${Library.count(Kind.SERIES)} bölüm"
        counts[Kind.MOVIE]?.text = "${Library.count(Kind.MOVIE)} film"
        status.text = ""
    }
}
