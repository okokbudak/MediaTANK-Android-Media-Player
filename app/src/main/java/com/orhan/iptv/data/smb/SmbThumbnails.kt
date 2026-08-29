package com.orhan.iptv.data.smb

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.orhan.iptv.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * NAS'taki fotoğraflar için küçük ön izleme üretir.
 *
 * Ağdan okuma pahalı olduğu için üç önlem var: küçültülmüş çözünürlükte
 * decode, bellek içi önbellek ve aynı öğe için tek iş. Liste hızlı
 * kaydırıldığında geri dönüştürülen satırların işi iptal ediliyor, yoksa
 * NAS gereksiz yere onlarca eşzamanlı istekle boğuluyor.
 *
 * Video ön izlemesi burada yok: tek kare çıkarmak dosyayı kısmen indirip
 * çözmeyi gerektiriyor ve DS509+ hızında bir NAS'ta liste kaydırmayı
 * kullanılamaz hale getiriyor.
 */
object SmbThumbnails {

    /** Kart yüksekliğine yakın; daha büyüğü boşuna bellek ve bant genişliği. */
    private const val TARGET_PX = 256

    /** ~4 MB: yüzlerce küçük ön izleme için fazlasıyla yeterli. */
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Devam eden işler; görünüm geri dönüştürülünce iptal edilebilsin. */
    private val jobs = HashMap<ImageView, Job>()

    fun cached(item: MediaItem): Bitmap? = cache.get(item.id)

    /**
     * [item] için ön izlemeyi [view]'e yükler.
     * Önbellekte varsa anında, yoksa arka planda okunur.
     */
    fun load(scope: CoroutineScope, view: ImageView, item: MediaItem, onLoaded: () -> Unit = {}) {
        cancel(view)

        cached(item)?.let {
            view.setImageBitmap(it)
            onLoaded()
            return
        }

        jobs[view] = scope.launch {
            val bitmap = decode(item)
            // Görünüm bu arada başka bir öğeye bağlandıysa sonucu atma.
            if (jobs[view]?.isActive != true) return@launch
            if (bitmap != null) {
                cache.put(item.id, bitmap)
                view.setImageBitmap(bitmap)
                onLoaded()
            }
            jobs.remove(view)
        }
    }

    fun cancel(view: ImageView) {
        jobs.remove(view)?.cancel()
    }

    private suspend fun decode(item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = SmbBrowser.readBytes(item)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sample = 1
            while (bounds.outWidth / sample > TARGET_PX * 2 ||
                bounds.outHeight / sample > TARGET_PX * 2
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return@runCatching null

            // Ön izleme de EXIF'e göre düzeltilmeli; yoksa ızgarada yan yatık
            // küçük kareler görünür.
            rotateByExif(bitmap, bytes)
        }.getOrNull()
    }

    private fun rotateByExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }

        return runCatching {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }
}
