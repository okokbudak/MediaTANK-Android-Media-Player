# MediaTANK

Android TV, tablet ve telefon için hepsi bir arada medya oynatıcı: IPTV canlı
yayın, film, dizi, ağ klasöründeki video ve fotoğraflar, USB ve NAS'taki müzik
— profil desteğiyle. Aynı uygulama televizyonda kumandayla, tablet ve telefonda
dokunmatik olarak kullanılır.

Kişisel kullanım için yazıldı, mağazaya yayınlanmıyor ve içerik barındırmıyor.
Playlist, abonelik ve dosyaları kullanıcı kendisi sağlar.

## Desteklenen cihazlar

Tek bir APK hepsinde çalışır. Arayüz, cihazın ekranına ve dokunmatik olup
olmadığına göre kendini ayarlar: televizyonda uzaktan okunacak büyüklükte ve
kumanda odağına göre, tablette ve telefonda dokunmaya uygun boyutta.

| | |
|---|---|
| Android | 5.0 ve üzeri (`minSdk 21`, `targetSdk 34`) |
| İşlemci | armeabi-v7a ve arm64-v8a |
| Televizyon | Android TV; kumandayla tam kullanım |
| Tablet / telefon | Dokunmatik: geri düğmesi, ses çubuğu, kaydırma jestleri |

Denendiği cihazlar: Sony Bravia `BRAVIA_ATV3_4K_EU` (Android 9), Lenovo tablet
(Android 16), Android TV ve telefon emülatörleri. NAS tarafında Synology
DS509+ / DSM 4.2 (yalnız SMB1) ile test edildi; SMB2/3 de desteklenir.

## Özellikler

**İki oynatma motoru.** ExoPlayer (Media3) canlı HLS/DASH için; libVLC her şey
için. Bir içerik ExoPlayer'da açılmazsa aynı saniyeden VLC'ye otomatik geçilir
ve o URL bir dahaki sefere doğrudan VLC ile açılır. Kumandadan elle de
geçilebilir.

**Codec kapsamı.** VLC'nin yazılımsal çözücüsü sayesinde H.264/HEVC/AV1/VP9
yanında VC-1, MPEG-2, eski DivX/Xvid, DTS, DTS-HD, TrueHD, EAC3 ve
PGS/VobSub/SRT/ASS altyazılar da çalışır.

**Kaynaklar.** M3U/M3U8 (URL veya yerel dosya), Xtream Codes (canlı + film +
dizi/bölüm), SMB ağ klasörleri.

**Profiller.** Favoriler, kaldığı yer, izlendi işaretleri ve dil tercihleri
profil başına ayrı; kaynaklar ortak.

**Arama.** Bölüm içinde veya hepsinde. Türkçe'ye duyarlı: "dugun" → "Düğün".

**Detay sayfası.** Özet, tür, oyuncu, yönetmen, yıldızlı puan; dizilerde sezon
ve bölüm seçimi. TMDB anahtarı girilirse bilgiler Türkçe olarak zenginleştirilir
ve kalıcı olarak önbelleğe alınır.

**Ağ klasörü.** Liste/kılavuz/karo görünümleri, fotoğraflarda gerçek ön izleme,
tam ekran fotoğraf görüntüleyici (EXIF'e göre otomatik döndürme, elle döndürme,
slayt gösterisi).

**Müzik çalar.** NAS klasörleri ve USB için ayrı sekme; çalma sırası, karışık,
tekrar.

**Kumanda (televizyon).** Medya tuşları, CH +/−, AUDIO, SUBTITLE, renkli
tuşlar, GUIDE, ACTION MENU ve rakam tuşları bağlı.

**Dokunmatik (tablet ve telefon).** Her ekranın üstünde "Geri" düğmesi;
oynatıcıda ses çubuğu ve kaydırma jestleri; fotoğraflarda kaydırarak gezinme.
Ayrıntı için aşağıdaki [Dokunmatik](#dokunmatik-tablet-ve-telefon) bölümü.

## Kurulum

Hazır APK için [Releases](../../releases) sayfasına bakın.

Gereken: JDK 17, Android SDK 34.

```bash
./gradlew assembleDebug
```

Emülatör için (x86 kütüphaneleriyle):

```bash
./gradlew assembleDebug -Pabis=x86
```

`local.properties` dosyası depoda yok; `sdk.dir` satırını kendiniz eklemeniz
gerekir.

### Yayın sürümü

```bash
./gradlew assembleRelease
```

Yayın derlemesi R8 ile küçültülüyor (`isMinifyEnabled`, `isShrinkResources`).
libVLC, jcifs ve Gson yansımayla çalıştığı için koruma kuralları
`app/proguard-rules.pro` içinde; oradaki `-keep` satırlarını kaldırmak
oynatıcıyı ve kayıtlı profilleri sessizce bozar.

İmzalama bilgileri depoya girmiyor. Anahtar deposunu bir kez kendiniz
oluşturun (parolayı yalnız siz girersiniz):

```bash
keytool -genkeypair -v -keystore mediatank.jks -alias mediatank -keyalg RSA -keysize 2048 -validity 10000
```

Sonra proje kökünde `keystore.properties` dosyasını oluşturun — bu dosya
`.gitignore` içinde, depoya gitmez:

```properties
storeFile=mediatank.jks
storePassword=...
keyAlias=mediatank
keyPassword=...
```

Dosya yoksa derleme hata vermez; yayın APK'sı hata ayıklama anahtarıyla
imzalanır. Böyle bir APK kurulup denenebilir ama dağıtılamaz.

`.jks` dosyasını ve parolasını kaybetmeyin: Play Store'a yüklenmiş bir
uygulamanın güncellemesi yalnız aynı anahtarla imzalanabiliyor.

### Televizyona yükleme

TV'de Ayarlar → Cihaz Tercihleri → Hakkında → *Yapı* satırına 7 kez basıp
geliştirici seçeneklerini açın, ardından **USB hata ayıklama**yı etkinleştirin.

```bash
adb connect TV_IP:5555
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

İlk bağlantıda TV ekranındaki yetkilendirme penceresini onaylayın.

### Tablete veya telefona yükleme

Android 11 ve üzerinde **Geliştirici seçenekleri → Kablosuz hata ayıklama**
açılır. İlk seferde bir kez eşleştirilir (**Eşleme kodu ile cihazı eşle**
penceresindeki port ve kodla):

```bash
adb pair CIHAZ_IP:ESLESTIRME_PORTU
```

Sonra, kablosuz hata ayıklama ana ekranında yazan **bağlantı** portuyla
(eşleştirme portundan farklıdır ve her açılışta değişir) bağlanıp kurulur:

```bash
adb connect CIHAZ_IP:BAGLANTI_PORTU
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Adb kullanmak istemezseniz [Releases](../../releases) sayfasından APK'yı
cihaza indirip dokunarak da kurabilirsiniz ("bilinmeyen kaynaklardan yükleme"
izni gerekir). Daha önce **hata ayıklama anahtarıyla** kurulmuş bir sürüm,
yayın anahtarıyla imzalı sürümün üzerine kurulamaz; önce kaldırılmalıdır.

## Kumanda (televizyon)

**Ana ekran** — ◀▶▲▼ gezinme, OK aç, **uzun bas** favorilere ekle/çıkar,
kırmızı tuş arama, yeşil görünüm, sarı sıralama, mavi ayarlar.

**Oynatıcı** — OK panel açar; paneldeki ⏮ ⏪ ⏯ ⏩ ⏭ düğmeleri ve kumandanın
kendi medya tuşları. AUDIO ses kanalı, SUBTITLE altyazı, 0–9 filmin yüzdesine
atlar. Panel kapalıyken yön tuşları yalnız paneli açar.

**Fotoğraf** — ◀▶ gezinme, OK slayt gösterisi, ▲▼ döndürme.

## Dokunmatik (tablet ve telefon)

Bu kontroller yalnızca parmakla dokunulan ekranlarda görünür. Televizyonda
hiçbiri yoktur; kumandanın odak sırası ve düzeni değişmez.

**Geri.** Detay, NAS gezgini, müzik gezgini, arama, ayarlar, kaynak ekleme ve
müzik çalar ekranlarının üstünde "← Geri" çubuğu bulunur. NAS ve müzik
gezginlerinde bir üst klasöre çıkar, en üstteyse ekrandan çıkar.

**Oynatıcı.**

| Jest | Etki |
|---|---|
| Tek dokunuş | Paneli açar / kapatır |
| Çift dokunuş, sol üçte bir | 10 sn geri |
| Çift dokunuş, sağ üçte bir | 10 sn ileri |
| Çift dokunuş, orta | Oynat / duraklat |
| Yatay kaydırma | Sarar (parmak kalkınca uygulanır) |
| Sağ yarıda dikey kaydırma | Ses |
| Sol yarıda dikey kaydırma | Parlaklık |

Panelde ayrıca geri düğmesi, ses çubuğu ve sessiz düğmesi vardır. Müzik çalarda
da ses çubuğu bulunur.

**Fotoğraf.** Sola/sağa kaydırarak önceki/sonraki fotoğraf, dokununca panel;
panelde önceki/sonraki okları ve geri düğmesi.

**Boyutlar.** Televizyon için ayarlı değerler (gövde yazısı 9sp, düğme 44dp)
tablette okunamayacak kadar küçüktü. Dokunmatik ekranlarda yazı büyür ve dokunma
hedefleri en az 48dp olur. Bu ayrım kaynak niteleyicileriyle yapılır
(`values-finger`, `values-w600dp-finger`).

## Mimari

```
data/
  Models.kt          Source, MediaItem, Profile, TrackOption
  Repository.kt      SharedPreferences; profil bazlı ad alanı
  CatalogLoader.kt   Kaynakları Catalog'a çevirir
  m3u/               #EXTINF ayrıştırma, per-item User-Agent
  xtream/            Xtream Codes API + içerik ayrıntısı
  smb/               jcifs-ng; SMB1↔SMB2.1 pazarlığı, ön izleme üretimi
  local/             USB ve dahili depolama gezgini
  meta/              TMDB istemcisi ve kalıcı önbellek
player/
  PlayerEngine.kt    İki motoru birleştiren arayüz
  ExoEngine.kt       Media3
  VlcEngine.kt       libVLC
  PlaybackActivity   Motor seçimi, geri düşüş, kanal menüleri, kumanda, jestler
  VolumeControl.kt   Ekrandaki ses çubuğu ve sessiz düğmesi
ui/
  TouchNav.kt        Dokunmatik ekranlara "Geri" çubuğu ekler
  profile/           "Kim izliyor?"
  MainActivity       Bölüm kutuları, kategori + içerik ızgarası
  detail/            Film/dizi detayı, sezon ve bölüm seçimi
  search/            Türkçe'ye duyarlı arama
  nas/               Ağ klasörü gezgini
  photo/             Fotoğraf görüntüleyici
  music/             Müzik gezgini ve çalar
  settings/          Dil, motor, TMDB anahtarı, kaynaklar
```

Görsel dil **Cinematic Pulse** tasarım sistemine göre: `#131313` zemin, `#ADC6FF` odak rengi, 16dp köşe
yarıçapı, odakta 1.1 ölçek. Televizyonda 40dp/30dp overscan güvenli alan
kullanılır; tablette kenar boşlukları daha dardır.

## Bilinen sınırlar ve bilinçli ödünleşimler

- **Sertifika doğrulaması kapalı** (`net/Http.kt`). IPTV portallarının çoğu
  geçerli sertifika sunmuyor. Kimlik bilgileri yalnız kullanıcının girdiği
  sunucuya gidiyor.
- **Puan TMDB'nin kendi oylaması**, IMDb değil. Ekranda kaynak belirtiliyor.
  IMDb puanı için OMDb eklenebilir (ücretsiz, 1.000 istek/gün).
- **Video ön izlemesi yok.** Tek kare çıkarmak dosyayı ağdan kısmen indirip
  çözmeyi gerektiriyor; eski bir NAS'ta liste kaydırmayı kullanılamaz hale
  getiriyor. Fotoğraflarda ön izleme var.
- **EPG yok.** Xtream EPG uç noktası bağlanmadı.
- **ExoPlayer'a akış ortasında harici altyazı eklenemiyor.** Harici `.srt`
  gerektiğinde VLC motoruna geçin; VLC yanındaki dosyayı kendiliğinden bulur.
- **Varsayılan yerel ayar `Locale.ROOT`'a sabitleniyor** (`App.kt`). Türkçe'de
  "i" harfinin büyüğü "İ" olduğu için jcifs paylaşım adını `FİTNESS` diye
  gönderiyor ve sunucu bulamıyordu; aynı tuzak `.AVI` uzantısını `.avı` yapıyordu.
- **Yayın APK'ları proje sahibinin anahtarıyla imzalı.** Kendi derlemenizi
  yaparsanız kendi anahtarınızı kullanın (bkz. *Yayın sürümü*); farklı imzalı
  sürümler birbirinin üzerine kurulmaz.
## Lisans

MediaTANK özgür yazılımdır. Copyright © 2026 Orhan Kokbudak.

Bu program özgür yazılımdır: Free Software Foundation tarafından yayımlanan
**GNU Genel Kamu Lisansı'nın 3. sürümü ya da (tercihinize göre) daha sonraki
herhangi bir sürümü** koşullarıyla yeniden dağıtabilir ve/veya değiştirebilirsiniz.

Bu program yararlı olması umuduyla dağıtılmaktadır, ancak **HİÇBİR GARANTİ
VERİLMEZ**; satılabilirlik ya da belirli bir amaca uygunluk garantisi dahil.
Ayrıntılar için GNU Genel Kamu Lisansı'na bakın. Lisansın tam metni
[LICENSE](LICENSE) dosyasındadır; ayrıca <https://www.gnu.org/licenses/gpl-3.0.html>.

SPDX: `GPL-3.0-or-later`

Pratikte bu şu demektir: kodu özgürce kullanabilir, inceleyebilir, değiştirebilir
ve paylaşabilirsiniz; ancak değiştirilmiş sürümü dağıtırsanız onu da aynı
lisansla, kaynak koduyla birlikte sunmanız gerekir.

Bağlanılan kütüphaneler kendi lisanslarını korur; **libVLC** ve **jcifs-ng**
LGPL-2.1 veya sonrası, AndroidX ve Media3 gibi çoğu bileşen Apache-2.0
kapsamındadır ve hepsi GPL-3.0-or-later ile uyumludur. APK'lar herkese açık
olarak dağıtıldığı için bu kütüphanelerin yükümlülükleri geçerlidir — bkz.
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

Film ve dizi bilgileri isteğe bağlı olarak TMDB'den geliyor:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

MediaTANK hiçbir içerik barındırmaz, sağlamaz veya dizinlemez. Playlist,
abonelik ve dosyalar kullanıcı tarafından sağlanır.
