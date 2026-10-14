package com.btstechnic.player

import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.media.AudioManager
import android.os.*
import android.util.Rational
import android.view.*
import android.widget.*
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Playback { var queue: List<Entry> = emptyList(); var index = 0 }

/**
 * YouTube tarzı oynatıcı:
 *  • Dokun: kontrolleri göster/gizle (3,5 sn sonra kendiliğinden gizlenir)
 *  • Çift dokun sol/sağ: 10 sn geri/ileri (arka arkaya dokundukça toplanır), ortada: oynat/duraklat
 *  • Sol yarıda dikey kaydır: parlaklık · sağ yarıda: ses
 *  • Tam ekran: dikey (16:9 + sıradakiler) ⇄ yatay (tam ekran), cihazı çevirince kilit kendiliğinden açılır
 *  • Ayarlar: oynatma hızı, kalite, görüntü oranı · Ekrandan çıkınca küçük pencere (PiP)
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private val http = DefaultHttpDataSource.Factory()
    private lateinit var frame: FrameLayout; private lateinit var pv: PlayerView; private lateinit var overlay: View
    private lateinit var spin: View; private lateinit var title: TextView; private lateinit var live: TextView
    private lateinit var pos: TextView; private lateinit var dur: TextView; private lateinit var seek: SeekBar
    private lateinit var play: ImageButton; private lateinit var prev: ImageButton; private lateinit var next: ImageButton
    private lateinit var fs: ImageButton; private lateinit var seekL: TextView; private lateinit var seekR: TextView
    private lateinit var hud: TextView; private lateinit var err: View; private lateinit var errMsg: TextView
    private lateinit var below: View; private lateinit var title2: TextView; private lateinit var sub: TextView
    private lateinit var nextList: ListView
    private lateinit var am: AudioManager
    private val h = Handler(Looper.getMainLooper())

    private var queue: List<Entry> = emptyList(); private var index = 0
    private var controls = false; private var scrubbing = false
    private var resize = 0
    private var burst = 0; private var burstSide = 0; private var burstUntil = 0L
    private var mode = 0                      // 0 belirsiz · 1 dikey kaydırma · 2 yok say
    private var bright = -1f; private var volF = 0f
    private var lock: Int? = null; private var orient: OrientationEventListener? = null
    private var retries = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        queue = Playback.queue; index = Playback.index.coerceIn(0, max(0, queue.lastIndex))
        if (queue.isEmpty()) { finish(); return }
        setContentView(R.layout.activity_player)
        frame = findViewById(R.id.frame); pv = findViewById(R.id.pv); overlay = findViewById(R.id.overlay); spin = findViewById(R.id.spin)
        title = findViewById(R.id.title); live = findViewById(R.id.live); pos = findViewById(R.id.pos); dur = findViewById(R.id.dur)
        seek = findViewById(R.id.seek); play = findViewById(R.id.play); prev = findViewById(R.id.prev); next = findViewById(R.id.next)
        fs = findViewById(R.id.fs); seekL = findViewById(R.id.seekL); seekR = findViewById(R.id.seekR); hud = findViewById(R.id.hud)
        err = findViewById(R.id.err); errMsg = findViewById(R.id.errMsg); below = findViewById(R.id.below)
        title2 = findViewById(R.id.title2); sub = findViewById(R.id.sub); nextList = findViewById(R.id.nextList)
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        seek.progressTintList = ColorStateList.valueOf(CYAN); seek.thumbTintList = ColorStateList.valueOf(CYAN)

        http.setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10000).setReadTimeoutMs(20000)
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
        pv.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                spin.visibility = if (s == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (s == Player.STATE_ENDED && !isLive() && index < queue.lastIndex) load(index + 1)
                refreshIcons()
            }
            override fun onIsPlayingChanged(p: Boolean) {
                pv.keepScreenOn = p; if (p) { retries = 0; err.visibility = View.GONE }
                refreshIcons(); if (controls) showControls()
            }
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) { player.seekToDefaultPosition(); player.prepare(); return }
                if (isLive() && retries < 3) { retries++; h.postDelayed({ player.prepare() }, 2000); return }
                errMsg.text = "Oynatılamadı. Adresi, bağlantınızı veya cihazın codec desteğini kontrol edin.\n(${e.errorCodeName})"
                err.visibility = View.VISIBLE; spin.visibility = View.GONE
            }
        })
        findViewById<View>(R.id.retry).setOnClickListener { retries = 0; load(index) }
        findViewById<View>(R.id.back).setOnClickListener { onBackPressed() }
        play.setOnClickListener { togglePlay() }
        prev.setOnClickListener { load(index - 1) }; next.setOnClickListener { load(index + 1) }
        fs.setOnClickListener { toggleFullscreen() }
        findViewById<View>(R.id.ratio).setOnClickListener { cycleRatio() }
        findViewById<View>(R.id.settings).setOnClickListener { settings() }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) { if (user) pos.text = fmt(p * 1000L) }
            override fun onStartTrackingTouch(s: SeekBar) { scrubbing = true; h.removeCallbacks(hideRun) }
            override fun onStopTrackingTouch(s: SeekBar) { player.seekTo(s.progress * 1000L); scrubbing = false; showControls() }
        })
        setupGestures()
        orient = object : OrientationEventListener(this) {
            override fun onOrientationChanged(d: Int) {
                val l = lock ?: return; if (d == ORIENTATION_UNKNOWN) return
                val land = d in 60..120 || d in 240..300; val port = d <= 30 || d >= 330
                if ((l == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && port) || (l == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE && land)) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; lock = null   // YouTube gibi: kilit açılır, cihaz dönmeyi izler
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        applyLayout(); load(index); showControls(); h.post(tick)
    }

    // ───────── yükleme / kuyruk ─────────
    private fun load(i: Int) {
        if (i !in queue.indices) return
        saveProgress(); index = i; Playback.index = i; retries = 0
        val e = queue[i]
        http.setUserAgent(if (e.ua.isNotEmpty()) e.ua else "BTS Technic Player/1.0")
        http.setDefaultRequestProperties(if (e.referer.isNotEmpty()) mapOf("Referer" to e.referer) else emptyMap())
        title.text = e.name; title2.text = e.name
        sub.text = (if (e.kind == Kind.LIVE) "Canlı yayın" else if (e.kind == Kind.SERIES) "Dizi" else "Film") + if (e.group.isNotEmpty()) " · ${e.group}" else ""
        err.visibility = View.GONE
        val saved = if (e.kind == Kind.LIVE) 0L else Prefs.pos(this, e.url)
        player.setMediaItem(MediaItem.fromUri(e.url), if (saved > 10_000) saved else C.TIME_UNSET)
        player.prepare(); player.playWhenReady = true
        prev.alpha = if (i > 0) 1f else .4f; next.alpha = if (i < queue.lastIndex) 1f else .4f
        val up = queue.drop(i + 1).take(60)
        nextList.adapter = object : ArrayAdapter<Entry>(this, android.R.layout.simple_list_item_1, up) {
            override fun getView(p: Int, v: View?, g: ViewGroup): View = (super.getView(p, v, g) as TextView).apply { text = up[p].name; setTextColor(0xFFEEF1FA.toInt()); textSize = 14f }
        }
        nextList.setOnItemClickListener { _, _, p, _ -> load(index + 1 + p) }
    }
    private fun isLive() = queue.getOrNull(index)?.kind == Kind.LIVE || player.isCurrentMediaItemLive

    // ───────── kontroller ─────────
    private val hideRun = Runnable { hideControls() }
    private fun showControls() {
        controls = true; overlay.visibility = View.VISIBLE; overlay.animate().cancel(); overlay.animate().alpha(1f).setDuration(150).start()
        h.removeCallbacks(hideRun); if (player.isPlaying && !scrubbing) h.postDelayed(hideRun, 3500)
    }
    private fun hideControls() {
        controls = false; overlay.animate().cancel()
        overlay.animate().alpha(0f).setDuration(200).withEndAction { if (!controls) overlay.visibility = View.GONE }.start()
    }
    private fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0); player.playWhenReady = true } else player.playWhenReady = !player.playWhenReady
        showControls()
    }
    private fun refreshIcons() {
        play.setImageResource(if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) R.drawable.ic_pause else R.drawable.ic_play)
    }
    private val tick = object : Runnable {
        override fun run() {
            val d = player.duration; val lv = isLive()
            live.visibility = if (lv) View.VISIBLE else View.GONE
            if (lv || d == C.TIME_UNSET || d <= 0) { seek.visibility = View.INVISIBLE; pos.text = ""; dur.text = "" }
            else if (!scrubbing) {
                seek.visibility = View.VISIBLE; seek.max = (d / 1000).toInt(); seek.progress = (player.currentPosition / 1000).toInt()
                seek.secondaryProgress = (player.bufferedPosition / 1000).toInt(); pos.text = fmt(player.currentPosition); dur.text = " / " + fmt(d)
            }
            h.postDelayed(this, 500)
        }
    }
    private fun fmt(ms: Long): String {
        val s = max(0L, ms) / 1000; val hh = s / 3600; val m = s % 3600 / 60; val ss = s % 60
        return if (hh > 0) "%d:%02d:%02d".format(hh, m, ss) else "%d:%02d".format(m, ss)
    }

    // ───────── hareketler ─────────
    private fun side(x: Float) = when { x < frame.width / 3f -> -1; x > frame.width * 2 / 3f -> 1; else -> 0 }
    private fun setupGestures() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val s = side(e.x)
                if (SystemClock.uptimeMillis() < burstUntil && s != 0) seekBy(s) else if (controls) hideControls() else showControls()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean { val s = side(e.x); if (s == 0) togglePlay() else seekBy(s); return true }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null) return false
                if (mode == 0) mode = if (abs(dy) > abs(dx) * 1.4f) 1 else 2
                if (mode != 1) return false
                val f = dy / frame.height * 1.3f
                if (e1.x < frame.width / 2f) {
                    val lp = window.attributes; if (bright < 0) bright = if (lp.screenBrightness >= 0) lp.screenBrightness else 0.5f
                    bright = (bright + f).coerceIn(0.02f, 1f); lp.screenBrightness = bright; window.attributes = lp
                    showHud("☀  %d%%".format((bright * 100).roundToInt()))
                } else {
                    val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    volF = (volF + f * mx).coerceIn(0f, mx.toFloat()); am.setStreamVolume(AudioManager.STREAM_MUSIC, volF.roundToInt(), 0)
                    showHud((if (volF < 0.5f) "🔇  " else "🔊  ") + "%d%%".format((volF / mx * 100).roundToInt()))
                }
                return true
            }
        })
        frame.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) { mode = 0; volF = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() }
            gd.onTouchEvent(e); true
        }
    }
    private val hideSeek = Runnable { seekL.visibility = View.GONE; seekR.visibility = View.GONE }
    private fun seekBy(side: Int) {
        if (isLive() || player.duration == C.TIME_UNSET) { showHud("Canlı yayında ileri/geri sarılamaz"); return }
        val now = SystemClock.uptimeMillis()
        if (now > burstUntil || burstSide != side) burst = 0
        burst += 10; burstSide = side; burstUntil = now + 800
        player.seekTo((player.currentPosition + side * 10_000L).coerceIn(0L, player.duration))
        val tv = if (side < 0) seekL else seekR; (if (side < 0) seekR else seekL).visibility = View.GONE
        tv.text = if (side < 0) "«  $burst sn" else "$burst sn  »"; tv.visibility = View.VISIBLE
        h.removeCallbacks(hideSeek); h.postDelayed(hideSeek, 600)
    }
    private val hideHud = Runnable { hud.visibility = View.GONE }
    private fun showHud(t: String) { hud.text = t; hud.visibility = View.VISIBLE; h.removeCallbacks(hideHud); h.postDelayed(hideHud, 900) }

    // ───────── tam ekran ─────────
    private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun toggleFullscreen() {
        val toPortrait = isLand()
        requestedOrientation = if (toPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        lock = requestedOrientation
    }
    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); applyLayout() }
    private fun applyLayout() {
        val land = isLand() && !(Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode)
        below.visibility = if (land) View.GONE else View.VISIBLE
        val dm = resources.displayMetrics
        frame.layoutParams = (frame.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (land || (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode)) ViewGroup.LayoutParams.MATCH_PARENT else min(dm.widthPixels, dm.heightPixels) * 9 / 16
        }
        fs.setImageResource(if (land) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)
        bars(land)
    }
    @Suppress("DEPRECATION")
    private fun bars(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = window.insetsController ?: return
            if (hide) { c.hide(WindowInsets.Type.systemBars()); c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE } else c.show(WindowInsets.Type.systemBars())
        } else window.decorView.systemUiVisibility = if (hide) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE else View.SYSTEM_UI_FLAG_VISIBLE
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (isLand() && !(Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode)) toggleFullscreen() else { @Suppress("DEPRECATION") super.onBackPressed() } }

    // ───────── ayarlar ─────────
    private fun cycleRatio() {
        resize = (resize + 1) % 3
        pv.resizeMode = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)[resize]
        showHud(arrayOf("Orijinal oran", "Doldur: kenarlardan kırpar", "Esnet: ekranı tamamen kaplar")[resize])
    }
    private fun settings() {
        h.removeCallbacks(hideRun)
        val items = arrayOf("Oynatma hızı  (${player.playbackParameters.speed}x)", "Kalite", "Görüntü oranı")
        AlertDialog.Builder(this).setTitle("Ayarlar").setItems(items) { _, w ->
            when (w) { 0 -> speedMenu(); 1 -> qualityMenu(); else -> cycleRatio() }
        }.setOnDismissListener { showControls() }.show()
    }
    private fun speedMenu() {
        val v = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f); val cur = v.indexOfFirst { it == player.playbackParameters.speed }
        AlertDialog.Builder(this).setTitle("Oynatma hızı").setSingleChoiceItems(Array(v.size) { "${v[it]}x" }, cur) { d, i -> player.setPlaybackSpeed(v[i]); d.dismiss() }.show()
    }
    private fun qualityMenu() {
        val hs = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }.filter { it > 0 }.distinct().sortedDescending()
        if (hs.size < 2) { showHud("Bu yayında kalite seçeneği yok"); return }
        val labels = arrayOf("Otomatik") + hs.map { "${it}p" }
        AlertDialog.Builder(this).setTitle("Kalite").setItems(labels) { _, i ->
            val maxH = if (i == 0) Int.MAX_VALUE else hs[i - 1]
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, maxH).build()
        }.show()
    }

    // ───────── yaşam döngüsü / PiP / tuşlar ─────────
    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= 26 && player.isPlaying) enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }
    override fun onPictureInPictureModeChanged(inPip: Boolean, c: Configuration) {
        super.onPictureInPictureModeChanged(inPip, c); if (inPip) hideControls(); applyLayout()
    }
    private fun saveProgress() {
        val e = queue.getOrNull(index) ?: return; val d = player.duration
        if (e.kind == Kind.LIVE || d == C.TIME_UNSET || d <= 0) return
        val p = player.currentPosition; if (p > d - 30_000) Prefs.clear(this, e.url) else Prefs.save(this, e.url, p)
    }
    override fun onStart() { super.onStart(); if (orient?.canDetectOrientation() == true) orient?.enable() }
    override fun onStop() {
        super.onStop(); orient?.disable(); saveProgress()
        if (!(Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode)) player.pause()
    }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); player.release(); super.onDestroy() }
    override fun onKeyDown(k: Int, e: KeyEvent): Boolean = when (k) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-1); showControls(); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(1); showControls(); true }
        KeyEvent.KEYCODE_MEDIA_NEXT -> { load(index + 1); true }
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { load(index - 1); true }
        else -> super.onKeyDown(k, e)
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("bts", Context.MODE_PRIVATE)
    fun pos(c: Context, url: String) = sp(c).getLong("pos_" + url.hashCode(), 0L)
    fun save(c: Context, url: String, p: Long) = sp(c).edit().putLong("pos_" + url.hashCode(), p).apply()
    fun clear(c: Context, url: String) = sp(c).edit().remove("pos_" + url.hashCode()).apply()
}
