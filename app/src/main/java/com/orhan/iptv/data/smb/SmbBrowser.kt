package com.orhan.iptv.data.smb

import android.util.Log
import com.orhan.iptv.data.Engine
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Source
import jcifs.CIFSContext
import jcifs.smb.NtStatus
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.Properties

/**
 * NAS (SMB) gezgini.
 *
 * Hedef NAS: Synology DS509+ / DSM 4.2. Bu sürümdeki Samba yalnızca SMB1
 * (NT LM 0.12) konuşur ve çoğu zaman NTLMv1 kimlik doğrulaması bekler. Bu
 * yüzden jcifs-ng, SMB1'e izin verecek ve gerekirse NTLMv1'e düşecek şekilde
 * yapılandırılıyor. Aynı yapılandırma modern NAS'larla SMB2.1'e de çıkabiliyor,
 * yani tek kod yolu her iki nesli de kapsıyor.
 *
 * Listeleme jcifs-ng ile, oynatma ise libVLC'nin kendi SMB modülüne
 * (`smb://kullanıcı:şifre@host/share/yol`) devrediliyor — dosya ağdan doğrudan
 * akıtılır, araya kopyalama girmez.
 */
object SmbBrowser {

    private val videoExtensions = setOf(
        "mkv", "mp4", "avi", "m4v", "mov", "wmv", "flv", "ts", "m2ts", "mts",
        "mpg", "mpeg", "vob", "webm", "ogv", "3gp", "divx", "rmvb", "iso", "ifo"
    )

    /** Android'in BitmapFactory'sinin çözebildiği biçimler. */
    private val photoExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
    )

    /** Müzik çalarda listelenen ses biçimleri (libVLC hepsini çözer). */
    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma",
        "alac", "aiff", "aif", "ape", "dsf", "mka", "mpc", "wv"
    )

    /**
     * Denenecek bağlantı kipleri, sırayla.
     *
     * SMB1↔SMB2 arası "çok protokollü pazarlık" DSM 4.2'nin eski Samba'sında
     * çalışmıyor: sunucu jcifs'in kabul etmediği bir dialect indeksi döndürüp
     * "invalid dialect version" hatasına yol açıyor. Bu yüzden her kipte
     * protokol aralığı tek bir aileye sabitleniyor ve kipler tek tek deneniyor.
     *
     * [lmCompatibility] 3 = NTLMv2, 0 = NTLMv1 + LM (en eski sunucular).
     */
    private data class Attempt(
        val minVersion: String,
        val maxVersion: String,
        val lmCompatibility: Int,
        /** NTLM alan adı. Eski Samba boş veya çalışma grubu adı bekleyebiliyor. */
        val domain: String?,
        val extendedSecurity: Boolean,
        /** null = jcifs varsayılanı (NetBIOS 139'a düşebilir), 445 = doğrudan TCP. */
        val port: Int?,
        val label: String
    )

    /**
     * Denenecek kipler, en olasıdan başlayarak.
     *
     * Gözlem: DSM 4.2 ile SMB1 el sıkışması tamamlanıyor ("Logon failure"
     * alınabiliyor), SMB2 ise 139/NetBIOS yolunda takılıyor — bu yüzden SMB2
     * denemeleri 445'e sabitlendi. Alan adı da değişkenlerden biri: jcifs'in
     * varsayılan "?" alanı eski Samba tarafından reddedilebiliyor.
     */
    private val attempts = listOf(
        Attempt("SMB1", "SMB1", 3, "WORKGROUP", true, null, "SMB1 · NTLMv2 · WORKGROUP"),
        Attempt("SMB1", "SMB1", 3, "", true, null, "SMB1 · NTLMv2 · alansız"),
        Attempt("SMB1", "SMB1", 2, "", false, null, "SMB1 · NTLM · alansız"),
        Attempt("SMB1", "SMB1", 0, "", false, null, "SMB1 · NTLMv1+LM · alansız"),
        Attempt("SMB1", "SMB1", 0, "WORKGROUP", false, null, "SMB1 · NTLMv1+LM · WORKGROUP"),
        Attempt("SMB202", "SMB210", 3, "WORKGROUP", true, 445, "SMB2 · NTLMv2 · 445"),
        Attempt("SMB202", "SMB210", 3, "", true, 445, "SMB2 · NTLMv2 · alansız · 445"),
        Attempt("SMB1", "SMB1", 3, null, true, null, "SMB1 · NTLMv2 · varsayılan alan")
    )

    private fun cifsContext(source: Source, attempt: Attempt): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", attempt.minVersion)
            setProperty("jcifs.smb.client.maxVersion", attempt.maxVersion)
            setProperty("jcifs.smb.client.lmCompatibility", attempt.lmCompatibility.toString())
            // Eski Samba imzalamayı desteklemiyor; zorunlu kılma.
            setProperty("jcifs.smb.client.signingPreferred", "false")
            setProperty("jcifs.smb.client.signingEnforced", "false")
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
            setProperty("jcifs.smb.client.useExtendedSecurity", attempt.extendedSecurity.toString())
            setProperty("jcifs.smb.client.disablePlainTextPasswords", "false")
            // Kısa zaman aşımları: sekiz kip denenirken kullanıcı dakikalarca beklemesin.
            setProperty("jcifs.smb.client.responseTimeout", "12000")
            setProperty("jcifs.smb.client.soTimeout", "20000")
            setProperty("jcifs.smb.client.connTimeout", "8000")

            // ÖNEMLİ: DNS çözümleyicisini devre dışı bırak.
            // jcifs-ng'nin DNS yolu javax.naming (JNDI) kullanıyor; bu paket
            // Android'de yok ve çağrıldığında NoClassDefFoundError atıyor.
            // Sunucuya IP ile bağlandığımız için ad çözümlemeye zaten gerek yok.
            setProperty("jcifs.resolveOrder", "BCAST")

            // DFS yönlendirmesi Synology'de kullanılmıyor; sorgusu bazı
            // sürümlerde hata döndürüp bağlantıyı düşürüyor.
            setProperty("jcifs.smb.client.dfs.disabled", "true")
        }

        val base = BaseContext(PropertyConfiguration(props))
        val user = source.username.orEmpty()
        return if (user.isBlank()) {
            base.withAnonymousCredentials()
        } else {
            base.withCredentials(
                NtlmPasswordAuthenticator(attempt.domain, user, source.password.orEmpty())
            )
        }
    }

    /**
     * Kurulmuş bağlamların önbelleği.
     *
     * Her listelemede yeni bir [BaseContext] üretmek gerçek bir kaynak sızıntısı:
     * jcifs-ng bağlam başına ayrı bir taşıma havuzu (TCP soketi + SMB oturumu)
     * tutuyor ve bunlar hiç kapanmıyordu. DS509+ gibi eski bir NAS'ta eşzamanlı
     * oturum sayısı sınırlı olduğundan, birkaç klasör gezildikten sonra sunucu
     * yeni bağlantıları reddediyor ve bunu "BAD_NETWORK_NAME" olarak
     * bildiriyordu — sanki paylaşım yokmuş gibi.
     *
     * Anahtar kimlik bilgilerini de içeriyor: kullanıcı şifreyi değiştirdiğinde
     * eski oturum yeniden kullanılmasın.
     */
    private val contextCache = HashMap<String, CIFSContext>()

    private fun contextKey(source: Source, attempt: Attempt): String = listOf(
        host(source),
        attempt.label,
        source.username.orEmpty(),
        source.password.orEmpty().hashCode().toString()
    ).joinToString("|")

    private fun cachedContext(source: Source, attempt: Attempt): CIFSContext =
        contextCache.getOrPut(contextKey(source, attempt)) { cifsContext(source, attempt) }

    private fun evictContext(source: Source, attempt: Attempt) {
        contextCache.remove(contextKey(source, attempt))?.let { runCatching { it.close() } }
    }

    /** Tek bir paylaşımın tanılama sonucu. */
    data class ShareProbe(
        val share: String,
        val ok: Boolean,
        /** Başarılıysa öğe sayısı. */
        val entries: Int = 0,
        /** Başarısızsa hatanın adı ve NT durum kodu. */
        val error: String? = null,
        val ntStatus: String? = null,
        /** Başarılıysa hangi kipin işe yaradığı. */
        val attempt: String? = null
    )

    /**
     * Sunucudaki her paylaşımı tek tek açmayı dener ve sonucu döndürür.
     *
     * Belirli paylaşımların neden açılmadığını tahmin etmek yerine ölçmek için:
     * hangi paylaşımın hangi NT durum koduyla reddedildiği tek bakışta görünür.
     * Kimlik bilgileri cihazda kalır.
     */
    @Throws(IOException::class)
    fun probeShares(source: Source): List<ShareProbe> {
        val shares = listShares(source).map { it.title }
        return shares.map { share ->
            try {
                val items = list(source, share, "")
                ShareProbe(
                    share = share,
                    ok = true,
                    entries = items.size,
                    attempt = workingAttempt[host(source)]?.label
                )
            } catch (e: Exception) {
                val status = (e as? jcifs.smb.SmbException)?.ntStatus
                ShareProbe(
                    share = share,
                    ok = false,
                    error = e.message ?: e.javaClass.simpleName,
                    ntStatus = status?.let { "0x%08X".format(it) }
                )
            }
        }
    }

    /** Sunucuya açık tüm bağlamları kapatır (kaynak ayarları değişince). */
    fun disconnect() {
        contextCache.values.forEach { runCatching { it.close() } }
        contextCache.clear()
        workingAttempt.clear()
        workingShareForm.clear()
    }

    /**
     * Kullanıcının girdiği adresten saf sunucu adını çıkarır.
     *
     * Yazılmış olabilecek `smb://` ön ekini, yolu ve portu atar. Port özellikle
     * atılıyor: kullanıcılar sık sık DSM web arayüzünün portunu (5000) yazıyor,
     * oysa SMB 139/445'te dinler. Port bırakılırsa hem yanlış hedefe bağlanılır
     * hem de kip bir port dayattığında "5000:445" gibi bozuk bir URL oluşur.
     */
    private fun host(source: Source): String =
        source.url.trim()
            .removePrefix("smb://")
            .trim('/')
            .substringBefore('/')
            .substringBefore(':')

    /** URL'de kullanılacak sunucu adresi; kip port dayatıyorsa eklenir. */
    private fun hostSpec(source: Source, attempt: Attempt): String =
        attempt.port?.let { "${host(source)}:$it" } ?: host(source)

    /**
     * jcifs'in beklediği `smb://host/share/path/` biçimi (kimlik bilgisi ayrı verilir).
     *
     * Adlar HAM bırakılıyor, yüzde-kodlanmıyor. jcifs URL'i çözmüyor: yola ne
     * yazarsak sunucuya birebir onu gönderiyor. Kodlanmış hâlde "Düğün"
     * klasörü sunucuya "D%C3%BC%C4%9F%C3%BCn" olarak gidip
     * "system cannot find the path specified" hatası veriyordu.
     *
     * (Oynatma URL'i bunun tersi: libVLC kodlanmış smb:// bekliyor —
     * bkz. [playbackUrl].)
     */
    private fun jcifsUrl(
        hostSpec: String,
        share: String,
        path: String,
        directory: Boolean
    ): String {
        val cleaned = path.trim('/')
        // jcifs klasörleri sondaki '/' ile tanır; dosyada olursa açılmaz.
        val suffix = when {
            cleaned.isEmpty() -> ""
            directory -> "$cleaned/"
            else -> cleaned
        }
        return "smb://$hostSpec/$share/$suffix"
    }

    /**
     * Paylaşım adı için denenecek biçimler.
     *
     * Yol parçalarının aksine paylaşım adı ham gönderildiğinde boşluk içeren
     * adlar ("Music Making") sunucudan "network name cannot be found" alıyor;
     * kodlanmış hâli ise bazı sunucularda çalışmıyor. Hangisinin geçerli
     * olduğu sunucuya göre değiştiği için ikisi de deneniyor ve çalışan biçim
     * paylaşım başına hatırlanıyor.
     */
    private val workingShareForm = HashMap<String, String>()

    private fun shareForms(share: String): List<String> {
        workingShareForm[share]?.let { return listOf(it) }
        val encoded = enc(share)
        return if (encoded == share) listOf(share) else listOf(share, encoded)
    }

    /**
     * [share] içindeki [path] için SmbFile üretir; paylaşım adının çalışan
     * biçimini bulana kadar sırayla dener.
     */
    private fun <T> withShareForm(
        context: CIFSContext,
        hostSpec: String,
        share: String,
        path: String,
        directory: Boolean = true,
        block: (SmbFile) -> T
    ): T {
        var lastError: Exception? = null
        for (form in shareForms(share)) {
            try {
                val result = block(SmbFile(jcifsUrl(hostSpec, form, path, directory), context))
                if (workingShareForm[share] != form) {
                    Log.i(TAG, "'$share' için çalışan biçim: '$form'")
                    workingShareForm[share] = form
                }
                return result
            } catch (e: Exception) {
                Log.w(TAG, "paylaşım biçimi '$form' başarısız: ${e.message}")
                lastError = e
            }
        }
        workingShareForm.remove(share)
        throw lastError ?: IOException("Paylaşım açılamadı: $share")
    }

    /**
     * Sunucudaki tüm paylaşılan klasörleri listeler.
     *
     * Kaynak eklenirken tek bir paylaşım adı vermek zorunda kalmamak için var:
     * kullanıcı NAS'ın tamamında gezinebiliyor. Yönetimsel paylaşımlar (C$,
     * IPC$, print$) ve yazıcı kuyrukları elenir.
     */
    @Throws(IOException::class)
    fun listShares(source: Source): List<MediaItem> =
        withFallback(source) { context, hostSpec ->
            val server = SmbFile("smb://$hostSpec/", context)
            server.listFiles()
                .asSequence()
                // Adları tam olarak sunucunun verdiği gibi görelim: görünmez
                // karakter veya beklenmedik son ek varsa buradan anlaşılır.
                .onEach { Log.i(TAG, "paylaşım bulundu: '${it.name}'") }
                .map { it.name.trimEnd('/') }
                .filter { it.isNotEmpty() && !it.endsWith("$") }
                .distinct()
                .sortedBy { it.lowercase() }
                .map { share ->
                    MediaItem(
                        id = "smb_share_${share.hashCode()}",
                        title = share,
                        kind = MediaKind.FOLDER,
                        // Paylaşım kökü: yol boş, paylaşımın kendisi başlık.
                        playUrl = "",
                        group = source.name,
                        smbUser = source.username,
                        smbPass = source.password,
                        smbRoot = "smb://${host(source)}/$share/",
                        smbPath = ""
                    )
                }
                .toList()
        }

    /**
     * [share] içindeki [path] klasörünü listeler ("" = paylaşım kökü).
     * Klasörler önce, sonra videolar, sonra fotoğraflar; hepsi alfabetik.
     */
    @Throws(IOException::class)
    fun list(source: Source, share: String, path: String): List<MediaItem> =
        withFallback(source) { context, hostSpec ->
            val children = withShareForm(context, hostSpec, share, path) { it.listFiles() }

            val folders = ArrayList<MediaItem>()
            val videos = ArrayList<MediaItem>()
            val photos = ArrayList<MediaItem>()
            val audio = ArrayList<MediaItem>()
            val root = "smb://${host(source)}/$share/"

            for (child in children) {
                val rawName = child.name          // klasörlerde sonda '/' bulunur
                val name = rawName.trimEnd('/')
                if (name.isEmpty() || name.startsWith(".")) continue

                val childPath = if (path.isEmpty()) name else "$path/$name"

                if (child.isDirectory) {
                    folders += MediaItem(
                        id = "smb_dir_${childPath.hashCode()}",
                        title = name,
                        kind = MediaKind.FOLDER,
                        playUrl = childPath,
                        group = source.name,
                        smbUser = source.username,
                        smbPass = source.password,
                        smbRoot = root,
                        smbPath = childPath
                    )
                    continue
                }

                // Locale.ROOT şart: Türkçe küçültmede ".AVI" → ".avı" oluyor
                // ve dosya video listesinden düşüyor.
                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                when (ext) {
                    in videoExtensions -> videos += MediaItem(
                        id = "smb_file_${childPath.hashCode()}",
                        title = name.substringBeforeLast('.'),
                        kind = MediaKind.FILE,
                        playUrl = playbackUrl(source, share, childPath),
                        group = source.name,
                        smbUser = source.username,
                        smbPass = source.password,
                        smbRoot = root,
                        smbPath = childPath,
                        // Ağ üzerindeki ham konteynerler VLC'de belirgin biçimde
                        // daha güvenilir açılıyor; ExoPlayer'ın SMB şeması yok.
                        preferredEngine = Engine.VLC
                    )

                    in photoExtensions -> photos += MediaItem(
                        id = "smb_photo_${childPath.hashCode()}",
                        title = name.substringBeforeLast('.'),
                        kind = MediaKind.PHOTO,
                        playUrl = playbackUrl(source, share, childPath),
                        group = source.name,
                        smbUser = source.username,
                        smbPass = source.password,
                        smbRoot = root,
                        smbPath = childPath
                    )

                    in audioExtensions -> audio += MediaItem(
                        id = "smb_audio_${childPath.hashCode()}",
                        title = name.substringBeforeLast('.'),
                        kind = MediaKind.AUDIO,
                        playUrl = playbackUrl(source, share, childPath),
                        group = source.name,
                        smbUser = source.username,
                        smbPass = source.password,
                        smbRoot = root,
                        smbPath = childPath,
                        preferredEngine = Engine.VLC
                    )
                }
            }

            folders.sortBy { it.title.lowercase(Locale.ROOT) }
            videos.sortBy { it.title.lowercase(Locale.ROOT) }
            photos.sortBy { it.title.lowercase(Locale.ROOT) }
            audio.sortBy { it.title.lowercase(Locale.ROOT) }
            folders + videos + audio + photos
        }

    /**
     * Bir SMB dosyasını tamamen okur. Fotoğraf görüntüleyici için var:
     * Glide'ın smb:// şeması yok, dosyalar da bir bitmap'e sığacak kadar küçük.
     *
     * [MediaItem.smbRoot] / [MediaItem.smbPath] dolu olmalı (SMB gezgininden
     * gelen her öğede dolu).
     */
    @Throws(IOException::class)
    fun readBytes(item: MediaItem): ByteArray {
        val root = item.smbRoot ?: throw IOException("Dosya konumu bilinmiyor")
        val path = item.smbPath ?: throw IOException("Dosya yolu bilinmiyor")

        // smbRoot "smb://host/share/" biçiminde ve kodlanmamış tutuluyor.
        val bare = root.removePrefix("smb://").trim('/')
        val hostPart = bare.substringBefore('/')
        val sharePart = bare.substringAfter('/', "")

        // Kimlik bilgileri MediaItem üzerinde taşınıyor; geçici bir Source kur.
        val source = Source(
            id = "tmp",
            type = com.orhan.iptv.data.SourceType.SMB,
            name = "",
            url = hostPart,
            username = item.smbUser,
            password = item.smbPass,
            share = sharePart
        )
        return withFallback(source) { context, hostSpec ->
            // Yol ham, paylaşım adı ise listelemede çalıştığı biçimde.
            withShareForm(context, hostSpec, sharePart, path, directory = false) { file ->
                file.inputStream.use { it.readBytes() }
            }
        }
    }

    /**
     * Bağlantıyı doğrular ve bulunan paylaşım sayısını döndürür.
     *
     * Paylaşım adı verilmemişse sunucunun paylaşım listesi okunur; verilmişse
     * yalnız o paylaşımın erişilebilirliği sınanır.
     */
    @Throws(IOException::class)
    fun verify(source: Source): Int {
        val share = source.share?.takeIf { it.isNotBlank() }
            ?: return listShares(source).size

        return withFallback(source) { context, hostSpec ->
            withShareForm(context, hostSpec, share, "") { root ->
                if (!root.exists()) throw IOException("Paylaşım bulunamadı: $share")
                root.listFiles()      // okuma yetkisini de doğrula
            }
            1
        }
    }

    /**
     * Önce NTLMv2 (modern NAS), sonra NTLMv1/LM (DSM 4.2 gibi eski Samba) dener.
     * Kimlik doğrulama hatası dışındaki hatalarda yeniden denemez.
     */
    /**
     * Bağlantı kiplerini sırayla dener; ilk çalışan kipi hatırlar.
     *
     * Sunucu başına önbellek önemli: her klasör listelemede üç kipi baştan
     * denemek TV'de gözle görülür bir gecikme yaratır.
     */
    private val workingAttempt = HashMap<String, Attempt>()

    private fun <T> withFallback(source: Source, block: (CIFSContext, String) -> T): T {
        val key = host(source)
        // Daha önce çalışan kip varsa onunla başla.
        val ordered = workingAttempt[key]
            ?.let { known -> listOf(known) + attempts.filterNot { it == known } }
            ?: attempts

        // jcifs paylaşım adını büyük harfe çevirerek gönderiyor. Cihaz dili
        // Türkçe ise "Fitness" → "FİTNESS" olup sunucuda bulunamıyor. Android
        // Activity oluştururken varsayılan yerel ayarı sistem diline geri
        // çevirebildiği için burada, kullanımdan hemen önce sabitliyoruz.
        Locale.setDefault(Locale.ROOT)

        var lastError: Exception? = null
        for (attempt in ordered) {
            try {
                val result = block(cachedContext(source, attempt), hostSpec(source, attempt))
                if (workingAttempt[key] != attempt) {
                    Log.i(TAG, "ÇALIŞAN KİP → $key : ${attempt.label}")
                    workingAttempt[key] = attempt
                }
                return result
            } catch (e: Exception) {
                Log.w(TAG, "başarısız [${attempt.label}] → ${e.javaClass.simpleName}: ${e.message}")
                lastError = e
                // Bozulmuş bir oturumu sonsuza dek yeniden kullanmayalım:
                // bu kipin bağlamını at, bir sonraki deneme temiz kursun.
                evictContext(source, attempt)
                // Bağlantı ve kimlik doğrulama kurulduysa hata artık kaynağa
                // özgüdür (paylaşım yok, kilitli veya yetki yok). Başka bir
                // protokol kipi denemek bunu değiştirmez; boşuna beklemeyelim.
                if (workingAttempt[key] != null && isResourceLevel(e)) {
                    throw asFriendly(e)
                }
            } catch (e: LinkageError) {
                // jcifs-ng Android'de eksik JDK sınıfları yüzünden Error da atabiliyor.
                Log.e(TAG, "SMB kütüphanesi bu cihazda yüklenemedi", e)
                lastError = IOException("SMB kütüphanesi yüklenemedi: ${e.message}")
            }
        }
        workingAttempt.remove(key)
        throw lastError?.let { asFriendly(it) } ?: IOException("NAS'a bağlanılamadı")
    }

    /**
     * Taşıma/kimlik hatası mı, yoksa belirli bir paylaşım-dosya hatası mı?
     * İkincisinde protokol kipi değiştirmenin faydası yok.
     */
    private fun isResourceLevel(e: Exception): Boolean {
        if (e is jcifs.smb.SmbAuthException) return false
        if (e !is jcifs.smb.SmbException) return false
        return when (e.ntStatus) {
            NtStatus.NT_STATUS_BAD_NETWORK_NAME,
            NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,
            NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND,
            NtStatus.NT_STATUS_ACCESS_DENIED -> true
            else -> false
        }
    }

    /** SMB durum kodlarını kullanıcının anlayacağı Türkçe açıklamaya çevirir. */
    private fun asFriendly(e: Exception): IOException {
        val status = (e as? jcifs.smb.SmbException)?.ntStatus
        val message = when {
            e is jcifs.smb.SmbAuthException ->
                "Kullanıcı adı veya şifre reddedildi."
            status == NtStatus.NT_STATUS_BAD_NETWORK_NAME ->
                "Bu paylaşıma bağlanılamadı. Synology'de klasör kilitli (şifreli " +
                    "ve bağlanmamış) olabilir ya da bu kullanıcının erişim izni yoktur."
            status == NtStatus.NT_STATUS_ACCESS_DENIED ->
                "Erişim reddedildi. Bu kullanıcının klasörde okuma izni yok."
            status == NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND ||
                status == NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND ->
                "Klasör veya dosya bulunamadı."
            else -> e.message ?: e.javaClass.simpleName
        }
        return IOException(message)
    }

    private const val TAG = "SmbBrowser"

    /**
     * libVLC'nin anladığı smb:// URL'ini üretir.
     * Kullanıcı adı/şifre yüzde-kodlanır; '@' veya ':' içeren şifreler URL'i bozmasın.
     */
    private fun playbackUrl(source: Source, share: String, path: String): String {
        val user = source.username.orEmpty()
        val credentials = if (user.isBlank()) "" else buildString {
            append(enc(user))
            source.password?.takeIf { it.isNotEmpty() }?.let { append(':').append(enc(it)) }
            append('@')
        }
        val encodedPath = path.split('/').joinToString("/") { enc(it) }
        return "smb://$credentials${host(source)}/${enc(share)}/$encodedPath"
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
