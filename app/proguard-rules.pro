# ---------------------------------------------------------------------------
# Yayın (release) derlemesi R8 ile küçültme yapıyor. Aşağıdaki kurallar,
# yansıma (reflection) veya yerli kod üzerinden erişilen sınıfların
# silinmesini/yeniden adlandırılmasını engelliyor.
# ---------------------------------------------------------------------------

# libVLC: JNI katmanı sınıf ve metot adlarını C tarafından arıyor, hepsi kalmalı.
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**

# jcifs-ng / BouncyCastle: SMB kipleri ve şifreleme sağlayıcıları yansımayla
# yükleniyor; ayrıca jcifs javax.naming'e referans veriyor (Android'de yok).
-keep class jcifs.** { *; }
-dontwarn jcifs.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn javax.servlet.**
-dontwarn org.ietf.jgss.**

# SLF4J: jcifs'in günlükçüsü; Android bağlayıcısı çalışma anında bulunuyor.
-dontwarn org.slf4j.**

# Gson: alan adları JSON anahtarlarıyla birebir eşleşiyor. Model sınıfları
# karartılırsa TMDB ve Xtream yanıtları boş çözülür.
#
# Tüm veri katmanı korunuyor. Yalnız model sınıflarını saymak yerine paketin
# tamamını tutuyoruz: SourceType, MediaKind ve Engine gibi enum'lar JSON'a
# ad(name) olarak yazılıyor ve karartılmış bir enum adı kaydedilmiş
# favorileri, profilleri ve devam listesini okunamaz hale getirirdi.
# Maliyeti birkaç düzine sınıf; kazancı sessiz veri kaybının olmaması.
-keep class com.orhan.iptv.data.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# TypeToken jenerik bilgisini çalışma anında okuyor.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**

# OkHttp / Okio: isteğe bağlı platform sınıflarına zayıf referanslar var.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Media3/ExoPlayer: çözücü seçimi ve uzantılar yansımayla aranıyor.
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-dontwarn com.bumptech.glide.**

# XML düzenlerinden çağrılan görünüm kurucuları ve android:onClick metotları.
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Yığın izlerinin okunabilir kalması için: satır numaraları korunuyor,
# dosya adı gizleniyor. Bir çökme raporu geldiğinde mapping.txt ile
# gerçek satıra geri çevrilebiliyor.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
