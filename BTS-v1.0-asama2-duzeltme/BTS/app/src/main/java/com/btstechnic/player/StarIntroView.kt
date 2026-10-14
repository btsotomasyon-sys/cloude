package com.btstechnic.player

import android.content.Context
import android.graphics.*
import android.view.View
import java.util.Random
import kotlin.math.*

/** Siyah ekranda yıldızlar savrularak birleşip logoyu oluşturur. Dokunulursa geçilir. */
class StarIntroView(c: Context, private val onEnd: () -> Unit) : View(c) {
    private class P(val tx: Float, val ty: Float, val sx: Float, val sy: Float, val d: Float, val col: Int, val z: Float, val sw: Float)
    private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.app_logo)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random()
    private var pts = listOf<P>()
    private var bg = listOf<FloatArray>()
    private var t0 = 0L
    private var ended = false
    private var size = 0f; private var ox = 0f; private var oy = 0f
    private val den = resources.displayMetrics.density

    init { setBackgroundColor(Color.BLACK); setOnClickListener { end() } }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        size = min(min(w, h) * 0.52f, 320f * den); ox = w / 2f - size / 2; oy = h / 2f - size / 2
        val n = 150
        val px = IntArray(n * n); Bitmap.createScaledBitmap(logo, n, n, true).getPixels(px, 0, n, 0, 0, n, n)
        val r = n * 0.22f; val far = hypot(w.toFloat(), h.toFloat()) / 2
        val list = ArrayList<P>()
        for (y in 0 until n step 2) for (x in 0 until n step 2) {
            val c = px[y * n + x]; if (Color.alpha(c) < 140) continue
            val dx = max(max(r - x, x - (n - 1 - r)), 0f); val dy = max(max(r - y, y - (n - 1 - r)), 0f)
            if (dx * dx + dy * dy > r * r) continue          // yuvarlak köşelerin dışını alma
            val a = rnd.nextFloat() * 6.283f; val rr = far * (0.5f + rnd.nextFloat() * 0.7f)
            list.add(P(ox + x / n.toFloat() * size, oy + y / n.toFloat() * size, w / 2f + cos(a) * rr, h / 2f + sin(a) * rr,
                rnd.nextFloat() * 0.9f, c or (0xFF shl 24), (rnd.nextFloat() * 1.4f + 0.8f) * den, (rnd.nextFloat() - 0.5f) * 2.2f))
        }
        pts = list
        bg = List(140) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h, rnd.nextFloat() * 1.3f + 0.3f, rnd.nextFloat() * 6f) }
        t0 = 0L
    }

    override fun onDraw(cv: Canvas) {
        if (t0 == 0L) t0 = System.nanoTime()
        val t = (System.nanoTime() - t0) / 1e9f
        paint.style = Paint.Style.FILL
        val fadeBg = max(0f, 1f - (t - 2.4f))
        for (s in bg) {
            paint.color = Color.WHITE
            paint.alpha = (255 * fadeBg * (0.25f + 0.55f * abs(sin(t * 1.6f + s[3])))).toInt().coerceIn(0, 255)
            cv.drawRect(s[0], s[1], s[0] + s[2] * den, s[1] + s[2] * den, paint)
        }
        val pa = if (t < 2.7f) 1f else max(0f, 1f - (t - 2.7f) / 0.7f)
        if (pa > 0f) for (p in pts) {
            val k = ((t - p.d * 0.8f) / 1.8f).coerceIn(0f, 1f); val e = 1f - (1f - k).pow(3)
            val w = (1f - e) * p.sw * 60f * sin(k * 3.14f) * den
            val x = p.sx + (p.tx - p.sx) * e + w; val y = p.sy + (p.ty - p.sy) * e - w * 0.6f
            paint.color = if (k > 0.85f) p.col else 0xFFBFEFFF.toInt()
            paint.alpha = (255 * pa * (0.35f + 0.65f * k)).toInt().coerceIn(0, 255)
            cv.drawRect(x, y, x + p.z, y + p.z, paint)
        }
        if (t > 2.6f) { paint.alpha = (255 * ((t - 2.6f) / 0.8f).coerceIn(0f, 1f)).toInt(); cv.drawBitmap(logo, null, RectF(ox, oy, ox + size, oy + size), paint) }
        if (t > 3.3f) {
            paint.color = 0xFFCFE9FF.toInt(); paint.alpha = (255 * (t - 3.3f).coerceIn(0f, 1f)).toInt()
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 13f * resources.displayMetrics.scaledDensity; paint.letterSpacing = 0.35f
            cv.drawText("BTS TECHNIC PLAYER", width / 2f, oy + size + 48f * den, paint)
        }
        if (t > 5f) end() else postInvalidateOnAnimation()
    }

    private fun end() { if (!ended) { ended = true; onEnd() } }
}
