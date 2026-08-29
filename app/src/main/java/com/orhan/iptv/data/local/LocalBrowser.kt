package com.orhan.iptv.data.local

import android.content.Context
import android.os.Environment
import com.orhan.iptv.data.Engine
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import java.io.File
import java.util.Locale

/**
 * TV'ye takılan USB belleği ve dahili depolamayı gezer.
 *
 * Android TV'de takılan diskler `/storage/<UUID>` altında görünür; hangi
 * yolun kullanılacağı üreticiye göre değiştiği için birkaç bilinen kök
 * taranıp okunabilir olanlar sunuluyor.
 */
object LocalBrowser {

    private val videoExtensions = setOf(
        "mkv", "mp4", "avi", "m4v", "mov", "wmv", "flv", "ts", "m2ts", "mts",
        "mpg", "mpeg", "vob", "webm", "ogv", "3gp", "divx", "rmvb", "iso"
    )

    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma",
        "alac", "aiff", "aif", "ape", "dsf", "mka", "mpc", "wv"
    )

    private val photoExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
    )

    /**
     * Gezilebilir kökler: dahili depolama ve takılı diskler.
     *
     * `/storage` altındaki her giriş denenmiyor; `self` ve `emulated` gibi
     * sistem girdileri elenip yalnız gerçekten okunabilen dizinler dönüyor.
     */
    fun roots(context: Context): List<MediaItem> {
        val roots = LinkedHashMap<String, File>()

        Environment.getExternalStorageDirectory()
            ?.takeIf { it.canRead() }
            ?.let { roots["Dahili depolama"] = it }

        // Uygulamaya özel dış depolama yolları, takılı diskleri de kapsıyor.
        context.getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { it.parentFile?.parentFile?.parentFile?.parentFile }
            .filter { it.canRead() }
            .forEach { roots.putIfAbsent(it.name, it) }

        File("/storage").listFiles()
            ?.filter { it.isDirectory && it.canRead() && it.name !in setOf("self", "emulated") }
            ?.forEach { roots.putIfAbsent(it.name, it) }

        return roots.map { (label, dir) ->
            MediaItem(
                id = "local_root_${dir.absolutePath.hashCode()}",
                title = label,
                kind = MediaKind.FOLDER,
                playUrl = dir.absolutePath,
                group = "Yerel"
            )
        }
    }

    /**
     * [path] klasörünü listeler.
     * Klasörler önce, sonra video, ses ve fotoğraflar; hepsi alfabetik.
     */
    fun list(path: String): List<MediaItem> {
        val dir = File(path)
        val children = dir.listFiles().orEmpty()

        val folders = ArrayList<MediaItem>()
        val videos = ArrayList<MediaItem>()
        val audio = ArrayList<MediaItem>()
        val photos = ArrayList<MediaItem>()

        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (!child.canRead()) continue

            if (child.isDirectory) {
                folders += MediaItem(
                    id = "local_dir_${child.absolutePath.hashCode()}",
                    title = child.name,
                    kind = MediaKind.FOLDER,
                    playUrl = child.absolutePath,
                    group = "Yerel"
                )
                continue
            }

            // Locale.ROOT: Türkçe küçültmede ".MP3" bozulmasın.
            val ext = child.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val title = child.name.substringBeforeLast('.')
            val uri = "file://${child.absolutePath}"

            when (ext) {
                in videoExtensions -> videos += MediaItem(
                    id = "local_video_${child.absolutePath.hashCode()}",
                    title = title,
                    kind = MediaKind.FILE,
                    playUrl = uri,
                    group = dir.name
                )

                in audioExtensions -> audio += MediaItem(
                    id = "local_audio_${child.absolutePath.hashCode()}",
                    title = title,
                    kind = MediaKind.AUDIO,
                    playUrl = uri,
                    group = dir.name,
                    // Yerel dosyalarda ExoPlayer daha hafif ve hızlı açılıyor.
                    preferredEngine = Engine.EXOPLAYER
                )

                in photoExtensions -> photos += MediaItem(
                    id = "local_photo_${child.absolutePath.hashCode()}",
                    title = title,
                    kind = MediaKind.PHOTO,
                    playUrl = uri,
                    group = dir.name
                )
            }
        }

        folders.sortBy { it.title.lowercase(Locale.ROOT) }
        videos.sortBy { it.title.lowercase(Locale.ROOT) }
        audio.sortBy { it.title.lowercase(Locale.ROOT) }
        photos.sortBy { it.title.lowercase(Locale.ROOT) }
        return folders + videos + audio + photos
    }
}
