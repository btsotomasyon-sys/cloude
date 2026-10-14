package com.btstechnic.player

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View

class IntroActivity : Activity() {
    private var mp: MediaPlayer? = null
    private var done = false

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(StarIntroView(this) { next() })
        mp = MediaPlayer.create(this, R.raw.startup)?.apply { start() }
    }

    private fun next() {
        if (done) return; done = true
        // Kayıtlı oturum varsa doğrudan ana sayfa, yoksa giriş ekranı
        startActivity(Intent(this, if (SessionStore.has(this)) HomeActivity::class.java else LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() { mp?.release(); super.onDestroy() }
}
