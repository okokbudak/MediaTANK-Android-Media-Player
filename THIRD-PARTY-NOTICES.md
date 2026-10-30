# Üçüncü Taraf Bildirimleri

MediaTANK'ın kendi kodu **GNU GPL sürüm 3 ya da sonrası** (GPL-3.0-or-later)
ile lisanslıdır (bkz. `LICENSE`). Uygulamanın bağlandığı kütüphaneler ise kendi
lisanslarını korur; bu bildirim onları listeler.

**Önemli:** Uygulamanın APK'ları GitHub Releases üzerinden herkese açık olarak
dağıtılıyor. Bu yüzden aşağıdaki kütüphanelerin dağıtım yükümlülükleri
**geçerlidir**.

## LGPL kapsamındaki kütüphaneler

Değiştirilmemiş halleriyle kullanılıyorlar: libVLC yerel kütüphane (`.so`) olarak
uygulamaya bağlanıyor, jcifs-ng bir Java kütüphanesi olarak paketleniyor.

LGPL'nin gerektirdiklerinin nasıl karşılandığı:

- **Lisans metni ve kaynak:** Bu depo, kütüphaneler hariç uygulamanın **tüm
  kaynağını** GPL-3.0-or-later altında içeriyor. Kütüphanelerin kendi kaynakları
  aşağıdaki bağlantılarda.
- **Kütüphaneyi değiştirebilme:** Kullanıcı, bu depoyu klonlayıp
  `app/build.gradle.kts` içindeki bağımlılık sürümlerini değiştirerek uygulamayı
  kütüphanelerin kendi sürümüyle yeniden derleyebilir. Yayın APK'sı R8 ile
  küçültülüyor; bunun için gereken yapılandırma `app/proguard-rules.pro`
  dosyasında açıkça duruyor.
- **Uyumluluk:** LGPL-2.1-or-later kütüphaneler, GPL-3.0-or-later ile
  birleştirilebilir.

| Kütüphane | Lisans | Kaynak |
|---|---|---|
| libVLC for Android (`org.videolan.android:libvlc-all`) | LGPL-2.1-or-later | https://code.videolan.org/videolan/vlc-android |
| jcifs-ng (`eu.agno3.jcifs:jcifs-ng`) | LGPL-2.1-or-later | https://github.com/AgNO3/jcifs-ng |

> **Not:** libVLC'nin çekirdeği LGPL'dir, ancak VLC dağıtımındaki bazı
> eklentiler GPL kapsamındadır. MediaTANK yalnız LGPL çekirdeği ve
> `libvlc-all` paketinin sunduğu bileşenleri kullanır. Uygulamayı yeniden
> dağıtacaksanız kullandığınız libVLC sürümünün eklenti lisanslarını
> doğrulayın.

## Apache 2.0 kapsamındaki kütüphaneler

| Kütüphane | Kaynak |
|---|---|
| AndroidX (core, appcompat, lifecycle, recyclerview, documentfile, exifinterface) | https://developer.android.com/jetpack |
| AndroidX Media3 / ExoPlayer | https://github.com/androidx/media |
| Kotlin ve kotlinx.coroutines | https://github.com/JetBrains/kotlin |
| OkHttp | https://square.github.io/okhttp/ |
| Gson | https://github.com/google/gson |

Apache 2.0, GPL-3.0 ile uyumludur. Lisansın tam metni:
https://www.apache.org/licenses/LICENSE-2.0

## Diğer

| Kütüphane | Lisans | Kaynak |
|---|---|---|
| Glide | BSD; bir kısmı MIT ve Apache 2.0 | https://github.com/bumptech/glide |
| SLF4J (API ve Android bağlayıcısı) | MIT | https://www.slf4j.org/ |
| Bouncy Castle | MIT benzeri (Bouncy Castle Licence) | https://www.bouncycastle.org/licence.html |

Bouncy Castle, jcifs-ng bağımlılığı olarak geliyor.

## Veri sağlayıcıları

### TMDB

Film ve dizi bilgileri isteğe bağlı olarak TMDB'den çekiliyor. Kullanıcı kendi
ücretsiz API anahtarını girmediği sürece TMDB'ye hiçbir istek gönderilmiyor.

TMDB'nin attribution şartı gereği:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

Türkçesi: *Bu ürün TMDB API'sini kullanır, ancak TMDB tarafından onaylanmış
veya sertifikalandırılmış değildir.*

TMDB kullanım şartları: https://www.themoviedb.org/api-terms-of-use

### IPTV ve ağ kaynakları

Uygulama hiçbir içerik barındırmaz, sağlamaz veya dizinlemez. Playlist,
abonelik ve dosyalar tamamen kullanıcı tarafından sağlanır; bunların
haklarına sahip olmak kullanıcının sorumluluğundadır.

## Tasarım

Arayüzün görsel dili ("Cinematic Pulse") proje için üretilmiş bir tasarım
sistemidir. Uygulama simgesi ve afişi projeye özgüdür.
