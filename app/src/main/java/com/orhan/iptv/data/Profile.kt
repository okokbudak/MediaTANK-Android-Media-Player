package com.orhan.iptv.data

import java.io.Serializable

/**
 * Bir izleyici profili (Netflix/Prime Video tarzı).
 *
 * Profil, kullanıcıya ait tercihlerin ad alanıdır: favoriler, kaldığı yer
 * kayıtları ve motor tercihi profil başına ayrı tutulur. Kaynaklar (M3U,
 * Xtream, NAS) ise cihaz genelindedir — aynı aboneliği herkes kullanır.
 */
data class Profile(
    val id: String,
    val name: String,
    /** Avatar rengi (ARGB). Profil seçme ekranındaki dairenin rengi. */
    val color: Int,
    /** Çocuk profili: yalnız izlemeye izin verir, kaynak ayarlarını gizler. */
    val kids: Boolean = false
) : Serializable {

    /** Avatarda gösterilen baş harf. */
    val initial: String get() = name.trim().take(1).uppercase().ifEmpty { "?" }

    companion object {
        /** Profil avatarları için palet (Cinematic Pulse ile uyumlu). */
        val PALETTE = intArrayOf(
            0xFF4B8EFF.toInt(),   // primary-container
            0xFF3834B6.toInt(),   // secondary-container
            0xFFEF6719.toInt(),   // tertiary-container
            0xFF00A97F.toInt(),
            0xFFB0348A.toInt(),
            0xFF8B90A0.toInt()
        )
    }
}
