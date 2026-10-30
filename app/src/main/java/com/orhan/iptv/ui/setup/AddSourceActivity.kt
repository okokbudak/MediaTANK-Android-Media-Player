package com.orhan.iptv.ui.setup

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.orhan.iptv.R
import com.orhan.iptv.data.CatalogLoader
import com.orhan.iptv.data.Repository
import com.orhan.iptv.data.Source
import com.orhan.iptv.data.SourceType
import com.orhan.iptv.data.smb.SmbBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kaynak ekleme ve düzenleme ekranı: M3U, Xtream Codes veya NAS (SMB).
 *
 * Alt taraftaki kayıtlı kaynak listesinden bir kaynak seçildiğinde form o
 * kaynağın bilgileriyle dolar ve kaydetme işlemi aynı kaydı günceller
 * (kimliği korunur, böylece favoriler ve kaldığı yer kayıtları bozulmaz).
 *
 * Kaydetmeden önce kaynak doğrulanıyor — TV'de yanlış yazılmış bir URL'i
 * ana ekranda boş liste olarak keşfetmek yerine burada söylemek daha iyi.
 */
class AddSourceActivity : AppCompatActivity() {

    private lateinit var screenTitle: TextView
    private lateinit var typeM3u: RadioButton
    private lateinit var typeXtream: RadioButton
    private lateinit var typeSmb: RadioButton
    private lateinit var fieldName: EditText
    private lateinit var fieldUrl: EditText
    private lateinit var fieldShare: EditText
    private lateinit var fieldUsername: EditText
    private lateinit var fieldPassword: EditText
    private lateinit var hint: TextView
    private lateinit var existingList: LinearLayout
    private lateinit var existingHint: TextView
    private lateinit var progress: View
    private lateinit var saveButton: TextView

    /** Düzenlenen kaynağın kimliği; null ise yeni kaynak ekleniyor. */
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_source)
        com.orhan.iptv.ui.TouchNav.install(this)

        screenTitle = findViewById(R.id.screen_title)
        typeM3u = findViewById(R.id.type_m3u)
        typeXtream = findViewById(R.id.type_xtream)
        typeSmb = findViewById(R.id.type_smb)
        fieldName = findViewById(R.id.field_name)
        fieldUrl = findViewById(R.id.field_url)
        fieldShare = findViewById(R.id.field_share)
        fieldUsername = findViewById(R.id.field_username)
        fieldPassword = findViewById(R.id.field_password)
        hint = findViewById(R.id.hint)
        existingList = findViewById(R.id.existing_list)
        existingHint = findViewById(R.id.existing_hint)
        progress = findViewById(R.id.progress)
        saveButton = findViewById(R.id.btn_save)

        findViewById<android.widget.RadioGroup>(R.id.type_group)
            .setOnCheckedChangeListener { _, _ -> applyType() }

        saveButton.setOnClickListener { save() }
        findViewById<View>(R.id.btn_cancel).setOnClickListener {
            // Düzenleme sırasında İptal yalnız formu boşaltsın; ekrandan çıkmasın.
            if (editingId != null) startNewSource() else finish()
        }
        findViewById<View>(R.id.btn_clear).setOnClickListener { confirmClearAll() }

        applyType()
        renderExisting()
        requestStorageIfNeeded()
    }

    /**
     * Yerel bir .m3u dosyası (USB bellek, dahili depolama) okunabilsin diye
     * depolama iznini önden ister. Reddedilirse yalnız http(s) playlistler
     * çalışır; uygulama akışı kesilmez.
     */
    private fun requestStorageIfNeeded() {
        if (Build.VERSION.SDK_INT > 32) return
        val permission = android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_STORAGE)
    }

    private fun currentType(): SourceType = when {
        typeXtream.isChecked -> SourceType.XTREAM
        typeSmb.isChecked -> SourceType.SMB
        else -> SourceType.M3U
    }

    /** Seçilen türe göre alanları göster/gizle ve ipucu metnini değiştir. */
    private fun applyType() {
        when (currentType()) {
            SourceType.M3U -> {
                fieldUrl.setHint(R.string.field_url)
                fieldShare.visibility = View.GONE
                fieldUsername.visibility = View.GONE
                fieldPassword.visibility = View.GONE
                hint.text = "URL veya cihazdaki dosya yolu.\n" +
                        "Örnek: http://sunucu.com/get.php?username=abc&password=123&type=m3u_plus\n" +
                        "Örnek: /sdcard/liste.m3u"
            }

            SourceType.XTREAM -> {
                fieldUrl.setHint(R.string.field_host)
                fieldShare.visibility = View.GONE
                fieldUsername.visibility = View.VISIBLE
                fieldPassword.visibility = View.VISIBLE
                hint.text = "Örnek sunucu: http://sunucu.com:8080  (yalnızca adres, /player_api.php yazmayın)"
            }

            SourceType.SMB -> {
                fieldUrl.setHint(R.string.field_smb_host)
                fieldShare.visibility = View.VISIBLE
                fieldUsername.visibility = View.VISIBLE
                fieldPassword.visibility = View.VISIBLE
                hint.text = "Sunucunun IP adresi veya adı.\n" +
                        "Paylaşım adını boş bırakırsanız sunucudaki tüm klasörler listelenir."
            }
        }
    }

    // --- Düzenleme ---

    /** Formu [source] ile doldurup düzenleme kipine geçer. */
    private fun startEditing(source: Source) {
        editingId = source.id
        screenTitle.setText(R.string.edit_source_title)
        saveButton.setText(R.string.btn_save)

        when (source.type) {
            SourceType.M3U -> typeM3u.isChecked = true
            SourceType.XTREAM -> typeXtream.isChecked = true
            SourceType.SMB -> typeSmb.isChecked = true
        }
        applyType()

        fieldName.setText(source.name)
        fieldUrl.setText(source.url)
        fieldShare.setText(source.share.orEmpty())
        fieldUsername.setText(source.username.orEmpty())
        fieldPassword.setText(source.password.orEmpty())

        fieldUrl.requestFocus()
        Toast.makeText(this, "${source.name} düzenleniyor", Toast.LENGTH_SHORT).show()
    }

    /** Formu boşaltıp yeni kaynak ekleme kipine döner. */
    private fun startNewSource() {
        editingId = null
        screenTitle.setText(R.string.add_source_title)
        fieldName.text.clear()
        fieldUrl.text.clear()
        fieldShare.text.clear()
        fieldUsername.text.clear()
        fieldPassword.text.clear()
        fieldUrl.requestFocus()
    }

    // --- Kaydetme ---

    private fun save() {
        val type = currentType()
        val url = fieldUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Adres alanı boş olamaz", Toast.LENGTH_SHORT).show()
            return
        }

        // Paylaşım adı isteğe bağlı: boş bırakılırsa NAS'ın tüm paylaşılan
        // klasörleri listelenir ve kullanıcı aralarında gezinir.
        val share = fieldShare.text.toString().trim()

        val username = fieldUsername.text.toString().trim().takeIf { it.isNotEmpty() }
        val password = fieldPassword.text.toString().takeIf { it.isNotEmpty() }
        if (type == SourceType.XTREAM && username == null) {
            Toast.makeText(this, "Xtream için kullanıcı adı gerekli", Toast.LENGTH_SHORT).show()
            return
        }

        val source = Source(
            // Düzenlemede kimlik korunuyor: addSource aynı kimliği bulup
            // üzerine yazıyor, böylece kayıt çoğalmıyor.
            id = editingId ?: Repository.newId(),
            type = type,
            name = fieldName.text.toString().trim().ifEmpty { defaultName(type, url) },
            url = url,
            username = username,
            password = password,
            share = share.takeIf { it.isNotEmpty() }
        )

        // Kimlik bilgileri değişmiş olabilir; eski SMB oturumlarını kapat ki
        // doğrulama gerçekten yeni bilgilerle yapılsın.
        SmbBrowser.disconnect()

        progress.visibility = View.VISIBLE
        saveButton.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching { verify(source) }
            progress.visibility = View.GONE
            saveButton.isEnabled = true

            result.fold(
                onSuccess = { summary ->
                    Repository.addSource(source)
                    Toast.makeText(this@AddSourceActivity, summary, Toast.LENGTH_LONG).show()
                    finish()
                },
                onFailure = { error ->
                    android.util.Log.w("AddSource", "Kaynak doğrulanamadı: $source", error)
                    // Hata metnini kalıcı bir pencerede göster: Toast TV'de
                    // okunmadan kayboluyor ve tanılama yapmayı zorlaştırıyor.
                    AlertDialog.Builder(this@AddSourceActivity)
                        .setTitle("Bağlanılamadı")
                        .setMessage(
                            "${error.javaClass.simpleName}\n\n${error.message ?: "Ayrıntı yok"}"
                        )
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            )
        }
    }

    /** Kaynağa gerçekten bağlanır; başarılıysa özet metin döner. */
    private suspend fun verify(source: Source): String = withContext(Dispatchers.IO) {
        when (source.type) {
            SourceType.SMB -> {
                val shareCount = SmbBrowser.verify(source)
                if (source.share.isNullOrBlank()) {
                    "Bağlandı: $shareCount klasör bulundu"
                } else {
                    "Bağlantı doğrulandı: ${source.share}"
                }
            }
            else -> {
                val catalog = CatalogLoader.load(source)
                if (catalog.isEmpty) throw IllegalStateException("Kaynak boş liste döndürdü")
                "${catalog.live.size} kanal, ${catalog.vod.size} film, ${catalog.series.size} dizi bulundu"
            }
        }
    }

    private fun defaultName(type: SourceType, url: String): String = when (type) {
        SourceType.SMB -> "Ağ Klasörü ${url.take(24)}"
        SourceType.XTREAM -> "Xtream ${url.removePrefix("http://").removePrefix("https://").take(24)}"
        SourceType.M3U -> "Playlist"
    }

    // --- Kayıtlı kaynak listesi ---

    private fun renderExisting() {
        existingList.removeAllViews()
        val sources = Repository.sources()

        if (sources.isEmpty()) {
            existingHint.setText(R.string.no_sources)
            return
        }
        existingHint.setText(R.string.saved_sources_hint)

        for (source in sources) {
            val row = layoutInflater.inflate(R.layout.item_row, existingList, false)
            row.findViewById<TextView>(R.id.title).text = source.name
            row.findViewById<TextView>(R.id.subtitle).apply {
                text = describe(source)
                visibility = View.VISIBLE
            }
            row.findViewById<android.widget.ImageView>(R.id.icon).setImageResource(
                when (source.type) {
                    SourceType.SMB -> R.drawable.ic_nas
                    SourceType.XTREAM -> R.drawable.ic_live
                    SourceType.M3U -> R.drawable.ic_movie
                }
            )

            val frame = row.findViewById<View>(R.id.card_frame)
            frame.setOnClickListener { startEditing(source) }
            frame.setOnLongClickListener { confirmDelete(source); true }

            existingList.addView(row)
        }
    }

    /** Listede parolayı göstermeden kaynağı tanıtan özet satır. */
    private fun describe(source: Source): String = when (source.type) {
        SourceType.XTREAM ->
            "Xtream · ${source.url} · ${source.username.orEmpty()}"
        SourceType.SMB ->
            "Ağ · ${source.url}" + (source.share?.let { " / $it" } ?: " · tüm klasörler")
        SourceType.M3U ->
            "M3U · ${source.url}"
    }

    private fun confirmDelete(source: Source) {
        AlertDialog.Builder(this)
            .setTitle("${source.name} silinsin mi?")
            .setMessage(describe(source))
            .setPositiveButton("Sil") { _, _ ->
                Repository.removeSource(source.id)
                if (editingId == source.id) startNewSource()
                renderExisting()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Tüm kaynaklar silinsin mi?")
            .setMessage("Kayıtlı ${Repository.sources().size} kaynağın hepsi silinecek.")
            .setPositiveButton("Hepsini sil") { _, _ ->
                Repository.clearSources()
                startNewSource()
                renderExisting()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private companion object {
        const val REQUEST_STORAGE = 1
    }
}
