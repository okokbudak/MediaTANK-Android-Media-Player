package com.orhan.iptv.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.orhan.iptv.data.Engine
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Repository
import java.util.Locale

/**
 * Storage Access Framework üzerinden yerel klasör gezgini.
 *
 * Android 11'den itibaren uygulamalar `/storage` altındaki dosyalara doğrudan
 * erişemiyor; kullanıcının klasörü açıkça seçmesi gerekiyor. Seçilen klasörün
 * izni kalıcı olarak alınıp saklanıyor, böylece her açılışta yeniden sormuyor.
 *
 * Android TV 9 gibi eski sürümlerde [LocalBrowser] hâlâ çalışıyor; hangisinin
 * kullanılacağına [useSaf] karar veriyor.
 */
object SafBrowser {

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
     * Ham dosya erişimi yerine SAF gerekiyor mu?
     *
     * Android 11 (API 30) kapsamlı depolamayı zorunlu kıldı. Altındaki
     * sürümlerde `File` gezgini daha pratik: kullanıcıya klasör seçtirmeden
     * USB'yi listeleyebiliyor.
     */
    fun useSaf(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R

    /** Kullanıcıya klasör seçtiren sistem penceresini açan intent. */
    fun pickFolderIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

    /**
     * Seçilen klasörün iznini kalıcı hale getirir ve kaydeder.
     * Kullanıcı her açılışta yeniden seçmek zorunda kalmasın.
     */
    fun rememberFolder(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        Repository.addLocalFolder(uri.toString())
    }

    fun forgetFolder(uriString: String) = Repository.removeLocalFolder(uriString)

    /** Kaydedilmiş klasörler; müzik ve yerel gezginin kök listesi. */
    fun roots(context: Context): List<MediaItem> =
        Repository.localFolders().mapNotNull { uriString ->
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null
            val doc = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
                ?: return@mapNotNull null
            if (!doc.canRead()) return@mapNotNull null

            MediaItem(
                id = "saf_root_${uriString.hashCode()}",
                title = doc.name ?: "Klasör",
                kind = MediaKind.FOLDER,
                playUrl = uriString,
                group = "Cihaz"
            )
        }

    /** [treeUri] ile temsil edilen klasörün içeriği. */
    fun list(context: Context, treeUri: String): List<MediaItem> {
        val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return emptyList()
        val doc = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
            ?: return emptyList()

        val folders = ArrayList<MediaItem>()
        val videos = ArrayList<MediaItem>()
        val audio = ArrayList<MediaItem>()
        val photos = ArrayList<MediaItem>()

        for (child in doc.listFiles()) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue

            if (child.isDirectory) {
                folders += MediaItem(
                    id = "saf_dir_${child.uri.hashCode()}",
                    title = name,
                    kind = MediaKind.FOLDER,
                    playUrl = child.uri.toString(),
                    group = "Cihaz"
                )
                continue
            }

            // Locale.ROOT: Türkçe küçültmede ".MP3" bozulmasın.
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val title = name.substringBeforeLast('.')
            val url = child.uri.toString()

            when (ext) {
                in videoExtensions -> videos += MediaItem(
                    id = "saf_video_${child.uri.hashCode()}",
                    title = title,
                    kind = MediaKind.FILE,
                    playUrl = url,
                    group = doc.name,
                    // content:// şemasını ExoPlayer doğrudan açabiliyor;
                    // libVLC'de bu şema için ek köprü gerekiyor.
                    preferredEngine = Engine.EXOPLAYER
                )

                in audioExtensions -> audio += MediaItem(
                    id = "saf_audio_${child.uri.hashCode()}",
                    title = title,
                    kind = MediaKind.AUDIO,
                    playUrl = url,
                    group = doc.name,
                    preferredEngine = Engine.EXOPLAYER
                )

                in photoExtensions -> photos += MediaItem(
                    id = "saf_photo_${child.uri.hashCode()}",
                    title = title,
                    kind = MediaKind.PHOTO,
                    playUrl = url,
                    group = doc.name
                )
            }
        }

        folders.sortBy { it.title.lowercase(Locale.ROOT) }
        videos.sortBy { it.title.lowercase(Locale.ROOT) }
        audio.sortBy { it.title.lowercase(Locale.ROOT) }
        photos.sortBy { it.title.lowercase(Locale.ROOT) }
        return folders + videos + audio + photos
    }

    /** Fotoğraf görüntüleyici için içerik baytları. */
    fun readBytes(context: Context, uriString: String): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
            ?: throw java.io.IOException("Dosya açılamadı")
}
