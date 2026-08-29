import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// İmzalama bilgileri depoya girmiyor. keystore.properties dosyası varsa yayın
// derlemesi imzalanır, yoksa imzasız üretilir ve derleme yine de başarılı olur
// (böylece anahtar deposu olmayan bir makinede proje derlenebiliyor).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasSigningConfig = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.orhan.iptv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.orhan.iptv"
        minSdk = 21          // Bravia ATV3 is API 28; 21 keeps it broad
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // libVLC her ABI için ~25 MB yerli kütüphane taşıyor. Varsayılan olarak
        // yalnız ARM üretiyoruz (hedef Sony Bravia ATV3); APK 98 MB yerine
        // ~54 MB oluyor. Emülatör için:  ./gradlew assembleDebug -Pabis=x86
        ndk {
            val requested = (project.findProperty("abis") as String?)
                ?: "armeabi-v7a,arm64-v8a"
            abiFilters += requested.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 kullanılmayan kodu ve kaynakları atıyor. Kuralların tamamı
            // proguard-rules.pro içinde; libVLC ve jcifs yansımayla
            // çalıştığı için korumasız bırakılamıyor.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Anahtar deposu yoksa hata vermek yerine hata ayıklama anahtarına
            // düşüyoruz: küçültülmüş sürüm böylece kurulup denenebiliyor.
            // Bu APK dağıtılamaz; mağaza hata ayıklama imzasını reddediyor.
            signingConfig = if (hasSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // applicationId bilerek değiştirilmiyor: son eki eklersek cihazda
            // kurulu sürüm ayrı bir uygulama sayılır ve kayıtlı kaynaklar,
            // profiller, favoriler orada kalır.
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module"
        )
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Leanback kullanılmıyor: arayüz baştan özel yazıldı ve Leanback'e
    // bağlanmak uygulamayı televizyona kilitliyordu.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Storage Access Framework ile klasör gezinmesi (Android 11+ zorunlu yol)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ExoPlayer / Media3 - native engine for live streams
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // libVLC - "all codecs" engine + native SMB/NFS (fallback and VOD default)
    implementation("org.videolan.android:libvlc-all:3.6.0")

    // Networking (M3U download + Xtream Codes API)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // NAS gezinme. jcifs-ng SMB1 *ve* SMB2/3 konuşur; hedef NAS (Synology
    // DS509+ / DSM 4.2) yalnızca SMB1 sunduğu için SMB2-only bir kütüphane
    // (smbj gibi) bu cihaza hiç bağlanamaz.
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("org.slf4j:slf4j-api:1.7.36")
    // BouncyCastle'ı açıkça eklemiyoruz: jcifs-ng kendi uyumlu sürümünü
    // (bcprov-jdk18on) getiriyor, ikinci bir sürüm sınıf çakışması yaratır.
    runtimeOnly("org.slf4j:slf4j-android:1.7.36")

    // Images
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Fotoğrafların EXIF yönelim etiketini okumak için (otomatik döndürme)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
