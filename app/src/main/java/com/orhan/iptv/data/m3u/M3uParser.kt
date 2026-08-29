package com.orhan.iptv.data.m3u

import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import java.io.BufferedReader
import java.util.Locale

/**
 * Genişletilmiş M3U (#EXTM3U / #EXTINF) ayrıştırıcı.
 *
 * Desteklenenler:
 *  - #EXTINF:-1 tvg-id="" tvg-name="" tvg-logo="" group-title="" user-agent="",Görünen Ad
 *  - #EXTGRP:Grup Adı
 *  - #EXTVLCOPT:http-user-agent=... / http-referrer=...
 *  - #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=...
 *  - #EXTHTTP:{"User-Agent":"..."}
 *  - Yorum ve boş satırlar
 *
 * User-Agent'a özel dikkat: sağlayıcıların çoğu playlist'te bir UA belirtiyor
 * ve başka bir UA ile gelen isteği 403 ile reddediyor. Bu değer öğeyle birlikte
 * taşınıp oynatıcıya aktarılıyor.
 */
object M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        var pendingTitle: String? = null
        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingGroup: String? = null
        var pendingUserAgent: String? = null
        var pendingReferer: String? = null
        var index = 0

        fun resetPending() {
            pendingTitle = null
            pendingAttrs = emptyMap()
            pendingGroup = null
            pendingUserAgent = null
            pendingReferer = null
        }

        reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTM3U", true) -> Unit

                line.startsWith("#EXTINF", true) -> {
                    val comma = line.lastIndexOf(',')
                    val meta = if (comma >= 0) line.substring(0, comma) else line
                    pendingTitle = if (comma >= 0) line.substring(comma + 1).trim() else null
                    pendingAttrs = attrRegex.findAll(meta)
                        .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2] }
                    pendingGroup = pendingAttrs["group-title"]
                    pendingUserAgent = pendingAttrs["user-agent"]
                    pendingReferer = pendingAttrs["referer"] ?: pendingAttrs["referrer"]
                }

                line.startsWith("#EXTGRP", true) -> {
                    pendingGroup = line.substringAfter(':', "").trim().ifEmpty { pendingGroup }
                }

                // VLC'ye özel yönergeler; UA/Referer taşıyabiliyorlar.
                line.startsWith("#EXTVLCOPT", true) -> {
                    val option = line.substringAfter(':', "")
                    val key = option.substringBefore('=').trim().lowercase(Locale.ROOT)
                    val value = option.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> pendingUserAgent = value
                        "http-referrer", "http-referer" -> pendingReferer = value
                    }
                }

                // Kodi ve bazı panellerin kullandığı biçimler.
                line.startsWith("#KODIPROP", true) || line.startsWith("#EXTHTTP", true) -> {
                    extractUserAgent(line)?.let { pendingUserAgent = it }
                }

                line.startsWith("#") -> Unit

                else -> {
                    val title = pendingTitle?.takeIf { it.isNotBlank() }
                        ?: pendingAttrs["tvg-name"]?.takeIf { it.isNotBlank() }
                        ?: "Öğe ${index + 1}"
                    items += MediaItem(
                        id = pendingAttrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: "m3u_$index",
                        title = title,
                        kind = guessKind(line, pendingGroup),
                        playUrl = line,
                        logoUrl = pendingAttrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = pendingGroup,
                        userAgent = pendingUserAgent?.takeIf { it.isNotBlank() },
                        referer = pendingReferer?.takeIf { it.isNotBlank() }
                    )
                    index++
                    resetPending()
                }
            }
        }
        return items
    }

    /** `User-Agent=X`, `"User-Agent":"X"` ve `user-agent=X` biçimlerini yakalar. */
    private val userAgentRegex =
        Regex("""user[-_]?agent"?\s*[=:]\s*"?([^"&\n]+)""", RegexOption.IGNORE_CASE)

    private fun extractUserAgent(line: String): String? =
        userAgentRegex.find(line)?.groupValues?.get(1)?.trim()

    private val vodExtensions =
        setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "mpg", "mpeg", "webm")

    /**
     * Öğenin türünü tahmin eder.
     *
     * Sıralama önemli: URL yolu en güvenilir ipucu, ardından grup adı, en son
     * dosya uzantısı gelir. Uzantıya öncelik verilirse "DIZI: Exxen" grubundaki
     * bir `.mp4` bölüm film sayılır — grup adı uzantıdan daha bilgilendirici.
     */
    private fun guessKind(url: String, group: String?): MediaKind {
        val lower = url.lowercase(Locale.ROOT)

        // Grup adı en güvenilir ipucu. Sağlayıcılar dizi bölümlerini de
        // /movie/ yolundan sunabiliyor ("DIZI: Exxen" grubundaki bölümler
        // gibi), bu yüzden grup URL yolundan önce değerlendiriliyor.
        val g = group?.lowercase(Locale.ROOT).orEmpty()
        if (g.contains("dizi") || g.contains("series")) return MediaKind.SERIES
        if (g.contains("film") || g.contains("movie") || g.contains("vod") ||
            g.contains("sinema")
        ) return MediaKind.VOD

        if (lower.contains("/series/")) return MediaKind.SERIES
        if (lower.contains("/movie/") || lower.contains("/vod/")) return MediaKind.VOD
        if (lower.contains("/live/")) return MediaKind.LIVE

        val ext = lower.substringAfterLast('.', "").substringBefore('?')
        if (ext in vodExtensions) return MediaKind.VOD

        // Uzantısız yollar (örn. .../kanal/12) canlı yayındır.
        return MediaKind.LIVE
    }
}
