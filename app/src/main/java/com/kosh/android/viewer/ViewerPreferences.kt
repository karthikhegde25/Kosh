package com.kosh.android.viewer

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.viewerStore: DataStore<Preferences> by preferencesDataStore(name = "kosh_viewer_prefs")

data class ViewerPreferences(
    val theme: ViewerTheme = ViewerTheme.LIGHT,
    /** 0 = follow the system brightness, otherwise window brightness 0.05..1. */
    val brightness: Float = 0f,
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val lockHorizontal: Boolean = false,
    val snapPages: Boolean = false,
    val autoHideToolbar: Boolean = true,
    val scrubberOnScroll: Boolean = true,
    val tapToTurn: Boolean = false,
    val keepScreenOn: Boolean = true,
    val orientation: OrientationLock = OrientationLock.AUTO,
    val quickZoom: Float = 2f,
    val doubleTapZoom: Float = 2f,
    val autoScrollSpeed: Int = 40,
    val rememberPasswords: Boolean = true,
)

class ViewerPreferencesRepository(private val context: Context) {

    val prefs: Flow<ViewerPreferences> = context.viewerStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val d = ViewerPreferences()
            ViewerPreferences(
                theme = p[THEME]?.let { n -> ViewerTheme.entries.firstOrNull { it.name == n } } ?: d.theme,
                brightness = (p[BRIGHTNESS] ?: d.brightness).let { if (it <= 0f) 0f else it.coerceIn(0.05f, 1f) },
                scrollDirection = p[SCROLL]?.let { n -> ScrollDirection.entries.firstOrNull { it.name == n } } ?: d.scrollDirection,
                lockHorizontal = p[LOCK_H] ?: d.lockHorizontal,
                snapPages = p[SNAP] ?: d.snapPages,
                autoHideToolbar = p[AUTO_HIDE] ?: d.autoHideToolbar,
                scrubberOnScroll = p[SCRUBBER] ?: d.scrubberOnScroll,
                tapToTurn = p[TAP_TURN] ?: d.tapToTurn,
                keepScreenOn = p[KEEP_ON] ?: d.keepScreenOn,
                orientation = p[ORIENT]?.let { n -> OrientationLock.entries.firstOrNull { it.name == n } } ?: d.orientation,
                quickZoom = clampScale(p[QUICK_ZOOM] ?: d.quickZoom),
                doubleTapZoom = clampScale(p[DOUBLE_ZOOM] ?: d.doubleTapZoom),
                autoScrollSpeed = (p[AS_SPEED] ?: d.autoScrollSpeed).coerceIn(MIN_AUTO_SCROLL_PX, MAX_AUTO_SCROLL_PX),
                rememberPasswords = p[REMEMBER] ?: d.rememberPasswords,
            )
        }

    suspend fun update(block: (MutablePrefs) -> Unit) {
        context.viewerStore.edit { block(MutablePrefs(it)) }
    }

    /** Thin typed wrapper so callers don't touch preference keys. */
    class MutablePrefs(private val p: androidx.datastore.preferences.core.MutablePreferences) {
        fun theme(v: ViewerTheme) { p[THEME] = v.name }
        fun brightness(v: Float) { p[BRIGHTNESS] = v }
        fun scrollDirection(v: ScrollDirection) { p[SCROLL] = v.name }
        fun lockHorizontal(v: Boolean) { p[LOCK_H] = v }
        fun snapPages(v: Boolean) { p[SNAP] = v }
        fun autoHideToolbar(v: Boolean) { p[AUTO_HIDE] = v }
        fun scrubberOnScroll(v: Boolean) { p[SCRUBBER] = v }
        fun tapToTurn(v: Boolean) { p[TAP_TURN] = v }
        fun keepScreenOn(v: Boolean) { p[KEEP_ON] = v }
        fun orientation(v: OrientationLock) { p[ORIENT] = v.name }
        fun quickZoom(v: Float) { p[QUICK_ZOOM] = v }
        fun doubleTapZoom(v: Float) { p[DOUBLE_ZOOM] = v }
        fun autoScrollSpeed(v: Int) { p[AS_SPEED] = v }
        fun rememberPasswords(v: Boolean) { p[REMEMBER] = v }
    }

    // ---- per-document zoom (restored when the same file is reopened) -----------------------------

    suspend fun loadZoom(documentKey: String): String? =
        runCatching { context.viewerStore.data.first()[stringPreferencesKey("zoom_$documentKey")] }.getOrNull()

    suspend fun saveZoom(documentKey: String, value: String) {
        runCatching { context.viewerStore.edit { it[stringPreferencesKey("zoom_$documentKey")] = value } }
    }

    // ---- remembered passwords (AES-GCM, key held in the Android Keystore) ------------------------

    suspend fun loadPassword(documentKey: String): String? = runCatching {
        val raw = context.viewerStore.data.first()[stringPreferencesKey("pw_$documentKey")] ?: return null
        PasswordCrypto.decrypt(raw)
    }.getOrNull()

    suspend fun savePassword(documentKey: String, password: String) {
        runCatching {
            val enc = PasswordCrypto.encrypt(password)
            context.viewerStore.edit { it[stringPreferencesKey("pw_$documentKey")] = enc }
        }
    }

    suspend fun forgetPassword(documentKey: String) {
        runCatching { context.viewerStore.edit { it.remove(stringPreferencesKey("pw_$documentKey")) } }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val SCROLL = stringPreferencesKey("scroll")
        val LOCK_H = booleanPreferencesKey("lock_h")
        val SNAP = booleanPreferencesKey("snap")
        val AUTO_HIDE = booleanPreferencesKey("auto_hide")
        val SCRUBBER = booleanPreferencesKey("scrubber")
        val TAP_TURN = booleanPreferencesKey("tap_turn")
        val KEEP_ON = booleanPreferencesKey("keep_on")
        val ORIENT = stringPreferencesKey("orient")
        val QUICK_ZOOM = floatPreferencesKey("quick_zoom")
        val DOUBLE_ZOOM = floatPreferencesKey("double_zoom")
        val AS_SPEED = intPreferencesKey("as_speed")
        val REMEMBER = booleanPreferencesKey("remember_pw")
    }
}

private object PasswordCrypto {
    private const val ALIAS = "kosh_viewer_passwords"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val body = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(body, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String {
        val (iv, body) = stored.split(":", limit = 2)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(c.doFinal(Base64.decode(body, Base64.NO_WRAP)), Charsets.UTF_8)
    }
}
