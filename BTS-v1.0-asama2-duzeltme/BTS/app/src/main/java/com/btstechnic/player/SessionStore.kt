package com.btstechnic.player

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Liste adresi cihazda AES-GCM ile (Android KeyStore) şifreli saklanır. Giriş bir kez yapılır. */
object SessionStore {
    private const val ALIAS = "bts_playlist"
    private const val FILE = "playlist.enc"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    fun has(c: Context) = File(c.filesDir, FILE).exists()

    fun save(c: Context, url: String) {
        val ci = Cipher.getInstance("AES/GCM/NoPadding"); ci.init(Cipher.ENCRYPT_MODE, key())
        val iv = ci.iv; val enc = ci.doFinal(url.toByteArray())
        val tmp = File(c.filesDir, "playlist.tmp")
        tmp.outputStream().use { it.write(iv.size); it.write(iv); it.write(enc) }
        tmp.renameTo(File(c.filesDir, FILE))
    }

    fun load(c: Context): String? = try {
        val b = File(c.filesDir, FILE).readBytes(); val n = b[0].toInt()
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(1, 1 + n)))
        String(ci.doFinal(b, 1 + n, b.size - 1 - n))
    } catch (e: Exception) { null }

    fun clear(c: Context) { File(c.filesDir, FILE).delete(); Library.all = emptyList() }
}
