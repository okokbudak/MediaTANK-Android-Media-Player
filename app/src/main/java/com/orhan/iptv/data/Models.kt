package com.orhan.iptv.data

import java.io.Serializable

/** Bir kaynağın türü. */
enum class SourceType { M3U, XTREAM, SMB }

/**
 * Kullanıcının eklediği içerik kaynağı.
 * M3U      -> [url] dolu
 * XTREAM   -> [url] (http://host:port), [username], [password] dolu
 * SMB      -> [url] host, [share], [username], [password] dolu
 */
data class Source(
    val id: String,
    val type: SourceType,
    val name: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val share: String? = null
) : Serializable

/** Medya türü. */
enum class MediaKind { LIVE, VOD, SERIES, FILE, FOLDER, PHOTO, AUDIO }

/**
 * Oynatılabilir (veya içine girilebilir) tek bir öğe.
 * [playUrl] FOLDER için klasör yolu, diğerleri için doğrudan stream URL'i.
 */
data class MediaItem(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val playUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val description: String? = null,
    /** Xtream serilerinde sezon/bölüm gezinmesi için. */
    val seriesId: String? = null,
    /** SMB için kimlik bilgileri; oynatıcı URL'e gömmek zorunda. */
    val smbUser: String? = null,
    val smbPass: String? = null,
    /**
     * Fotoğrafları jcifs ile okumak için gereken konum bilgisi.
     * [smbRoot] "smb://host/share/", [smbPath] paylaşım köküne göreli yol.
     * (Oynatma URL'inden türetilemiyor çünkü orada kimlik bilgileri gömülü ve
     * yüzde-kodlanmış durumda.)
     */
    val smbRoot: String? = null,
    val smbPath: String? = null,
    /** Bu öğe için tercih edilen motor; null ise otomatik seçilir. */
    val preferredEngine: Engine? = null,
    /**
     * Bu akış için zorunlu User-Agent. Birçok IPTV sağlayıcısı playlist'te
     * `user-agent="okhttp/4.10.0"` gibi bir değer belirtiyor ve başka bir
     * UA ile istek geldiğinde 403 dönüyor. Boşsa uygulama varsayılanı kullanılır.
     */
    val userAgent: String? = null,
    /** Sağlayıcının istediği Referer başlığı (bazı portallar zorunlu tutuyor). */
    val referer: String? = null
) : Serializable

enum class Engine { EXOPLAYER, VLC }

/** Oynatıcıdan gelen tek bir ses/altyazı kanalı. */
data class TrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val selected: Boolean,
    /** Motorun kendi iç indeksi (VLC track id / ExoPlayer group index). */
    val engineIndex: Int = -1,
    val groupIndex: Int = -1,
    val trackIndexInGroup: Int = -1
)
