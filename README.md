# MediaTANK

Sony Bravia (Android TV 9) için hepsi bir arada medya oynatıcı: IPTV canlı
yayın, film, dizi, ağ klasöründeki video ve fotoğraflar, USB ve NAS'taki müzik
— profil desteğiyle.

Kişisel kullanım için yazıldı, mağazaya yayınlanmıyor ve içerik barındırmıyor.
Playlist, abonelik ve dosyaları kullanıcı kendisi sağlar.

## Hedef donanım

| | |
|---|---|
| TV | Sony Bravia `BRAVIA_ATV3_4K_EU` |
| Android | 9 (API 28), güvenlik yaması 5 Ocak 2022 |
| Çekirdek | 4.9.125 |
| ABI | armeabi-v7a |
| NAS | Synology DS509+ / DSM 4.2 (SMB1 + SMB2) |

`minSdk 21` ile derleniyor.

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

**Kumanda.** Medya tuşları, CH +/−, AUDIO, SUBTITLE, renkli tuşlar, GUIDE,
ACTION MENU ve rakam tuşları bağlı.

## Kurulum

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

### TV'ye yükleme

TV'de Ayarlar → Cihaz Tercihleri → Hakkında → *Yapı* satırına 7 kez basıp
geliştirici seçeneklerini açın, ardından **USB hata ayıklama**yı etkinleştirin.

```bash
adb connect TV_IP:5555
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

İlk bağlantıda TV ekranındaki yetkilendirme penceresini onaylayın.

## Kumanda

**Ana ekran** — ◀▶▲▼ gezinme, OK aç, **uzun bas** favorilere ekle/çıkar,
kırmızı tuş arama, yeşil görünüm, sarı sıralama, mavi ayarlar.

**Oynatıcı** — OK panel açar; paneldeki ⏮ ⏪ ⏯ ⏩ ⏭ düğmeleri ve kumandanın
kendi medya tuşları. AUDIO ses kanalı, SUBTITLE altyazı, 0–9 filmin yüzdesine
atlar. Panel kapalıyken yön tuşları yalnız paneli açar.

**Fotoğraf** — ◀▶ gezinme, OK slayt gösterisi, ▲▼ döndürme.

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
  PlaybackActivity   Motor seçimi, geri düşüş, kanal menüleri, kumanda
ui/
  profile/           "Kim izliyor?"
  MainActivity       Bölüm kutuları, kategori + içerik ızgarası
  detail/            Film/dizi detayı, sezon ve bölüm seçimi
  search/            Türkçe'ye duyarlı arama
  nas/               Ağ klasörü gezgini
  photo/             Fotoğraf görüntüleyici
  music/             Müzik gezgini ve çalar
  settings/          Dil, motor, TMDB anahtarı, kaynaklar
```

Görsel dil `design-ref/cinematic_pulse/DESIGN.md` dosyasındaki **Cinematic
Pulse** sistemine göre: `#131313` zemin, `#ADC6FF` odak rengi, 16dp köşe
yarıçapı, odakta 1.1 ölçek, 40dp/30dp overscan güvenli alan.

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
- APK imzasız (debug). Kalıcı kullanım için release imzası üretilmeli.

## Lisans

**Her hakkı saklıdır.** Kişisel bir proje; yayınlanmıyor, dağıtılmıyor ve
başkasının kullanımına açılmıyor. Ayrıntı için [LICENSE](LICENSE).

Bağlanılan kütüphaneler kendi lisanslarını koruyor; özellikle **libVLC** ve
**jcifs-ng** LGPL-2.1 kapsamında. Bu yükümlülükler yalnız dağıtım durumunda
doğar — bkz. [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

Film ve dizi bilgileri isteğe bağlı olarak TMDB'den geliyor:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

MediaTANK hiçbir içerik barındırmaz, sağlamaz veya dizinlemez. Playlist,
abonelik ve dosyalar kullanıcı tarafından sağlanır.
