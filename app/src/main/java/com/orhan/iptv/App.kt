package com.orhan.iptv

import android.app.Application
import com.orhan.iptv.data.Repository
import com.orhan.iptv.data.meta.MetadataCache
import java.util.Locale

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        forceNeutralCasing()
        Repository.init(this)
        MetadataCache.init(this)
    }

    /**
     * Varsayılan yerel ayarı ROOT yapar; harf büyütme/küçültme dile göre
     * değişmesin.
     *
     * Neden gerekli: Türkçe'de "i" harfinin büyüğü "İ"dir. Cihaz dili Türkçe
     * olduğunda `String.toUpperCase()` çağıran her kütüphane bundan etkileniyor.
     * jcifs, SMB tree-connect için paylaşım adını büyük harfe çeviriyor ve
     * "Fitness" sunucuya "FİTNESS" olarak gidip "böyle bir paylaşım yok"
     * hatası alıyordu — adında "i" geçmeyen "photo" ise çalışıyordu.
     *
     * Aynı tuzak kendi kodumuzda da var: ".AVI" uzantısı Türkçe küçültmeyle
     * ".avı" oluyor ve video listesinden düşüyor.
     *
     * Arayüz metinleri string kaynaklarından geldiği için bundan etkilenmiyor.
     * Türkçe'ye duyarlı küçültme gereken tek yer arama; orası zaten
     * Locale("tr") ile açıkça yapılıyor.
     */
    private fun forceNeutralCasing() {
        Locale.setDefault(Locale.ROOT)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
