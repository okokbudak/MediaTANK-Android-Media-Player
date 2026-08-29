package com.orhan.iptv.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Ortak OkHttp istemcisi.
 *
 * IPTV portallarının çoğu kendi imzaladığı sertifika kullanıyor ve bazıları
 * "VLC" dışındaki User-Agent'ları reddediyor. Bu yüzden:
 *  - User-Agent olarak VLC dizgesi gönderiyoruz,
 *  - yönlendirmeleri (http->https dahil) izliyoruz,
 *  - sertifika doğrulamasını gevşetiyoruz.
 *
 * Not: sertifika doğrulamasının kapalı olması bilinçli bir ödünleşim. Bu uygulama
 * ev ağında kullanıcının kendi IPTV/NAS sunucularına bağlanıyor ve bu sunucular
 * neredeyse hiçbir zaman geçerli bir zincir sunmuyor. Kimlik bilgileri yalnızca
 * kullanıcının kendi girdiği sunucuya gidiyor.
 */
object Http {

    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    val client: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), SecureRandom())
        }

        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)   // uzun playlist indirmeleri için sınırsız
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    @Throws(IOException::class)
    fun execute(request: Request): Response {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("Sunucu $code döndü")
        }
        return response
    }

    @Throws(IOException::class)
    fun string(request: Request): String = execute(request).use { it.body?.string().orEmpty() }

    /** Çağıran akışı kapatmakla yükümlü. */
    @Throws(IOException::class)
    fun stream(request: Request): InputStream =
        execute(request).body?.byteStream() ?: throw IOException("Boş yanıt")
}
