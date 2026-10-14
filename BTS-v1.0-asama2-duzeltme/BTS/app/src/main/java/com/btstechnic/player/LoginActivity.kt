package com.btstechnic.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

class LoginActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var steps: LinearLayout
    private lateinit var test: Button
    private lateinit var login: Button
    private var verified = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(56), dp(22), dp(24)) }
        col.addView(ImageView(this).apply { setImageResource(R.drawable.app_logo) }, LinearLayout.LayoutParams(dp(84), dp(84)))
        col.addView(text("Hoş geldiniz", 24f, Color_.WHITE, bold = true).apply { setPadding(0, dp(14), 0, 0) })
        col.addView(text("M3U listenizi bir kez ekleyin, her açılışta doğrudan ana sayfaya girin.", 13f, MUTED).apply { setPadding(0, dp(4), 0, dp(18)) })
        url = EditText(this).apply {
            hint = "M3U liste adresi (http / https)"; setHintTextColor(MUTED); setTextColor(Color_.WHITE); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setSingleLine()
            background = rounded(CARD, dp(12).toFloat(), 1, 0xFF262E4A.toInt()); setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        col.addView(url, LinearLayout.LayoutParams(-1, -2))
        test = button("Bağlantıyı test et", true) { runTest() }
        col.addView(test, lp(12))
        steps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(steps, lp(10))
        login = button("Giriş yap ve kaydet", true) { SessionStore.save(this, verified); startActivity(Intent(this, HomeActivity::class.java)); finish() }
            .apply { visibility = View.GONE }
        col.addView(login, lp(6))
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(col) })
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun text(s: String, sp: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = sp; setTextColor(c); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun button(s: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 15f; setTextColor(0xFF04122E.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, BLUE))
            .apply { cornerRadius = dp(12).toFloat() }
        setOnClickListener { onClick() }
    }

    /** Her adım: ekranda satır ekler, arka planda çalışır, ✓ / ✗ / ⚠ ile sonuçlanır. */
    private fun step(label: String, warnOnly: Boolean = false, block: () -> Unit) {
        val tv = text("…  $label", 13f, Color_.WHITE).apply { setPadding(dp(12), dp(9), dp(12), dp(9)); background = rounded(CARD, dp(10).toFloat()) }
        runOnUiThread { steps.addView(tv, lp(6)) }
        try { block(); runOnUiThread { tv.text = "✓  $label"; tv.setTextColor(0xFF2ECC8F.toInt()) } }
        catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            runOnUiThread { tv.text = (if (warnOnly) "⚠  $label: " else "✗  $label: ") + msg; tv.setTextColor(if (warnOnly) 0xFFFFB86B.toInt() else 0xFFFF5D6C.toInt()) }
            if (!warnOnly) throw e
        }
    }

    private fun runTest() {
        val u = url.text.toString().trim()
        steps.removeAllViews(); login.visibility = View.GONE; test.isEnabled = false
        Executors.newSingleThreadExecutor().execute {
            try {
                step("Adres biçimi geçerli") {
                    val uri = Uri.parse(u)
                    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrEmpty()) { "Geçerli bir HTTP veya HTTPS liste adresi girin." }
                }
                var body = ""
                step("Sunucuya erişildi ve liste indirildi") { body = Net.text(u) }
                step("M3U başlığı bulundu") { require(body.removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U")) { "Bu adres bir M3U kanal listesi değil." } }
                var list = emptyList<Entry>()
                step("Kanallar okundu") { list = M3u.parse(body); require(list.isNotEmpty()) { "Listede oynatılabilir içerik bulunamadı." } }
                step("Örnek yayın açıldı", warnOnly = true) {
                    val c = Net.open((list.firstOrNull { it.kind == Kind.LIVE } ?: list.first()).url)
                    try { require(c.responseCode in 200..299) { "HTTP ${c.responseCode}" } } finally { c.disconnect() }
                }
                Library.all = list; verified = u
                runOnUiThread { login.visibility = View.VISIBLE }
            } catch (e: Exception) { /* hata satırı zaten gösterildi */ }
            runOnUiThread { test.isEnabled = true }
        }
    }

    private object Color_ { const val WHITE = 0xFFEEF1FA.toInt() }
}
