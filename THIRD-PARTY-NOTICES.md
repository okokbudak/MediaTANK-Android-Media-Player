# Üçüncü Taraf Bildirimleri

MediaTANK'ın kendi kodu her hakkı saklı olarak tutuluyor (bkz. `LICENSE`).
Uygulamanın bağlandığı kütüphaneler ise kendi lisanslarını korur; bu bildirim
onları listeler.

**Önemli:** Aşağıdaki LGPL yükümlülükleri **dağıtımda** doğar. Uygulama yalnız
kendi cihazlarınızda derlenip kurulduğu sürece bir yükümlülük oluşmuyor.
APK'yı başkasına verdiğiniz veya yayınladığınız anda geçerli hale gelirler.

## LGPL kapsamındaki kütüphaneler

Dinamik olarak bağlanıyorlar ve değiştirilmemiş halleriyle kullanılıyorlar.
Dağıtım durumunda LGPL, kullanıcının bu kütüphaneleri kendi sürümleriyle
değiştirebilmesini ve lisans metninin birlikte verilmesini gerektirir.

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
| AndroidX (core, appcompat, lifecycle, recyclerview, exifinterface, leanback) | https://developer.android.com/jetpack |
| AndroidX Media3 / ExoPlayer | https://github.com/androidx/media |
| Kotlin ve kotlinx.coroutines | https://github.com/JetBrains/kotlin |
| OkHttp | https://square.github.io/okhttp/ |
| Retrofit | https://square.github.io/retrofit/ |
| Gson | https://github.com/google/gson |
| Glide | https://github.com/bumptech/glide |
| SLF4J Android | https://www.slf4j.org/android/ |

Apache 2.0 lisansının tam metni: https://www.apache.org/licenses/LICENSE-2.0

## Diğer

| Kütüphane | Lisans | Kaynak |
|---|---|---|
| Bouncy Castle | MIT benzeri (Bouncy Castle Licence) | https://www.bouncycastle.org/licence.html |

jcifs-ng bağımlılığı olarak geliyor.

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

Arayüzün görsel dili (`design-ref/cinematic_pulse/DESIGN.md`) proje için
üretilmiş bir tasarım sistemidir. Uygulama simgesi ve afişi projeye özgüdür.
