package com.orhan.iptv.ui.photo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.local.SafBrowser
import com.orhan.iptv.data.smb.SmbBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * NAS albümleri için tam ekran fotoğraf görüntüleyici.
 *
 * Fotoğraflar jcifs ile okunuyor (Glide'ın smb:// şeması yok) ve ekran
 * çözünürlüğüne indirgenerek çözülüyor — 40 MP'lik bir JPEG'i tam boyutta
 * açmak TV'nin belleğini tüketir.
 */
class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var image: ImageView
    private lateinit var progress: View
    private lateinit var title: TextView
    private lateinit var counter: TextView
    private lateinit var slideshowState: View
    private lateinit var photoControls: View

    /** Fotoğraf başına kullanıcının elle uyguladığı döndürme (derece). */
    private val manualRotation = HashMap<String, Float>()

    private var photos: List<MediaItem> = emptyList()
    private var index = 0
    private var loadJob: Job? = null
    private var slideshow = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideHint = Runnable { photoControls.visibility = View.GONE }
    private val advance = object : Runnable {
        override fun run() {
            if (!slideshow) return
            show((index + 1) % photos.size)
            handler.postDelayed(this, SLIDESHOW_INTERVAL_MS)
        }
    }

    /** Komşu kareler önden yüklenince ileri/geri gezinme anında olur. */
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)

        image = findViewById(R.id.photo)
        progress = findViewById(R.id.progress)
        title = findViewById(R.id.photo_title)
        counter = findViewById(R.id.photo_counter)
        slideshowState = findViewById(R.id.slideshow_state)
        photoControls = findViewById(R.id.photo_controls)

        @Suppress("DEPRECATION")
        photos = (intent.getSerializableExtra(EXTRA_PHOTOS) as? Array<*>)
            ?.filterIsInstance<MediaItem>().orEmpty()
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (photos.size - 1).coerceAtLeast(0))

        if (photos.isEmpty()) {
            Toast.makeText(this, "Gösterilecek fotoğraf yok", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<View>(R.id.btn_rotate_left).setOnClickListener { rotateBy(-90f) }
        findViewById<View>(R.id.btn_rotate_right).setOnClickListener { rotateBy(90f) }
        findViewById<View>(R.id.btn_slideshow).setOnClickListener { toggleSlideshow() }

        show(index)
        handler.postDelayed(hideHint, HINT_TIMEOUT_MS)
    }

    /**
     * Kullanıcının elle uyguladığı ek döndürme.
     *
     * EXIF etiketi olmayan veya yanlış olan fotoğraflar için. Görüntü yeniden
     * çözülmüyor, yalnız ImageView döndürülüyor — anında ve bellek harcamıyor.
     * Fotoğraf başına ayrı tutuluyor ki diğerlerine taşmasın.
     */
    private fun rotateBy(degrees: Float) {
        val current = manualRotation.getOrDefault(photos[index].id, 0f)
        val updated = (current + degrees + 360f) % 360f
        manualRotation[photos[index].id] = updated
        applyRotation()
        showControls()
    }

    private fun applyRotation() {
        image.rotation = manualRotation.getOrDefault(photos[index].id, 0f)
        // 90/270 derecede kare, görünümün dar kenarına sığmalı; yoksa taşar.
        val quarter = (image.rotation % 180f) != 0f
        if (quarter && image.width > 0 && image.height > 0) {
            val scale = minOf(
                image.height.toFloat() / image.width,
                image.width.toFloat() / image.height
            )
            image.scaleX = scale
            image.scaleY = scale
        } else {
            image.scaleX = 1f
            image.scaleY = 1f
        }
    }

    private fun showControls() {
        photoControls.visibility = View.VISIBLE
        handler.removeCallbacks(hideHint)
        handler.postDelayed(hideHint, HINT_TIMEOUT_MS)
    }

    private fun show(position: Int) {
        index = position
        val item = photos[index]

        title.text = item.title
        counter.text = getString(R.string.photo_of, index + 1, photos.size)

        cache.get(item.id)?.let {
            progress.visibility = View.GONE
            image.setImageBitmap(it)
            applyRotation()
            prefetchNeighbours()
            return
        }

        progress.visibility = View.VISIBLE
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val bitmap = decode(item)
            // Kullanıcı bu arada başka kareye geçtiyse sonucu atla.
            if (index != position) return@launch
            progress.visibility = View.GONE
            if (bitmap == null) {
                Toast.makeText(
                    this@PhotoViewerActivity,
                    "${item.title} açılamadı",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                cache.put(item.id, bitmap)
                image.setImageBitmap(bitmap)
                applyRotation()
            }
            prefetchNeighbours()
        }
    }

    private fun prefetchNeighbours() {
        if (photos.size < 2) return
        lifecycleScope.launch {
            for (offset in intArrayOf(1, -1)) {
                val neighbour = photos[(index + offset + photos.size) % photos.size]
                if (cache.get(neighbour.id) != null) continue
                decode(neighbour)?.let { cache.put(neighbour.id, it) }
            }
        }
    }

    /**
     * Fotoğrafı ekran boyutuna indirgeyerek çözer.
     * Önce yalnız başlığı okuyup ölçeği hesaplıyoruz; ikinci geçiş baytları
     * yeniden ağdan çekmiyor çünkü tamamı bellekte.
     */
    private suspend fun decode(item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readBytes(item)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565   // TV'de bellek yarıya iner
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return@runCatching null

            // Fotoğrafı EXIF etiketine göre düzelt. Telefonlar kareyi çoğu
            // zaman sensör yönünde kaydedip doğru yönü etikete yazar; etiket
            // uygulanmazsa fotoğraf yan yatık görünür.
            applyExifRotation(bitmap, bytes)
        }.getOrNull()
    }

    /**
     * Fotoğrafın baytlarını kaynağına göre okur.
     *
     * NAS fotoğrafları jcifs ile, cihazdakiler content:// üzerinden geliyor;
     * ikincisi Android 11+ Storage Access Framework yolunun karşılığı.
     */
    private fun readBytes(item: MediaItem): ByteArray = when {
        item.playUrl.startsWith("content://") ->
            SafBrowser.readBytes(this, item.playUrl)

        item.playUrl.startsWith("file://") ->
            java.io.File(item.playUrl.removePrefix("file://")).readBytes()

        else -> SmbBrowser.readBytes(item)
    }

    /** EXIF yönelim etiketini bitmap'e uygular. Etiket yoksa kare olduğu gibi döner. */
    private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        val metrics = resources.displayMetrics
        var sample = 1
        while (width / sample > metrics.widthPixels * 2 || height / sample > metrics.heightPixels * 2) {
            sample *= 2
        }
        return sample
    }

    private fun toggleSlideshow() {
        slideshow = !slideshow
        slideshowState.visibility = if (slideshow) View.VISIBLE else View.GONE
        handler.removeCallbacks(advance)
        if (slideshow) handler.postDelayed(advance, SLIDESHOW_INTERVAL_MS)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                show((index + 1) % photos.size)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                show((index - 1 + photos.size) % photos.size)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                toggleSlideshow()
                return true
            }
            // Yön tuşlarıyla döndürme: kumandada ayrı bir tuş yok.
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PROG_GREEN -> {
                rotateBy(90f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PROG_RED -> {
                rotateBy(-90f)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        // Ekran arkaplandayken slayt gösterisi ilerlemesin.
        slideshow = false
        handler.removeCallbacks(advance)
        slideshowState.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        loadJob?.cancel()
        cache.evictAll()
    }

    companion object {
        private const val EXTRA_PHOTOS = "photos"
        private const val EXTRA_INDEX = "index"
        private const val SLIDESHOW_INTERVAL_MS = 5_000L
        private const val HINT_TIMEOUT_MS = 4_000L
        /** Üç tam kare için yeterli; TV'lerde uygulama yığını dar. */
        private const val CACHE_BYTES = 48 * 1024 * 1024

        fun start(context: Context, photos: List<MediaItem>, startIndex: Int) {
            context.startActivity(
                Intent(context, PhotoViewerActivity::class.java)
                    .putExtra(EXTRA_PHOTOS, photos.toTypedArray())
                    .putExtra(EXTRA_INDEX, startIndex)
            )
        }
    }
}
