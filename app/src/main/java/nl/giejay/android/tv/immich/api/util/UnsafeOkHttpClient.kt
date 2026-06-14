package nl.giejay.android.tv.immich.api.util

import nl.giejay.android.tv.immich.api.util.Tls12SocketFactory.Companion.enableTls12
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object UnsafeOkHttpClient {
    fun unsafeOkHttpClient(): OkHttpClient.Builder = try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts: Array<TrustManager> = arrayOf(
                object : X509TrustManager {
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate?>?,
                        authType: String?
                    ) {
                    }

                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(
                        chain: Array<X509Certificate?>?,
                        authType: String?
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return emptyArray()
                    }
                }
            )

            // Install the all-trusting trust manager. Use TLSv1.2 so the resulting sockets can
            // negotiate it on KitKat (API 19), where it is otherwise off by default.
            val sslContext: SSLContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustAllCerts, SecureRandom())

            // Wrap the all-trusting factory so TLS 1.2 is force-enabled on pre-Lollipop devices.
            val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory
            val builder: OkHttpClient.Builder = OkHttpClient.Builder()
                .enableTls12(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
            builder
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
}