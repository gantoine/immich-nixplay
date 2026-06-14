package nl.giejay.android.tv.immich.api.util

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import timber.log.Timber
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android 4.4 (API 19/KitKat) ships TLS 1.2 but leaves it disabled on sockets by default, so
 * HTTPS handshakes to servers that require TLS 1.2 (i.e. any modern Immich instance) fail with
 * an SSL handshake error. This factory wraps a delegate [SSLSocketFactory] and force-enables
 * TLS 1.0/1.1/1.2 on every socket it produces.
 */
class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        patch(delegate.createSocket(s, host, port, autoClose))

    override fun createSocket(host: String?, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        patch(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        patch(delegate.createSocket(address, port, localAddress, localPort))

    private fun patch(socket: Socket): Socket {
        if (socket is SSLSocket) {
            socket.enabledProtocols = arrayOf("TLSv1", "TLSv1.1", "TLSv1.2")
            // KitKat (API 19) ships strong cipher suites as supported-but-disabled. Enable every
            // suite the device supports so we can find common ground with a modern server, which
            // typically won't accept the weak CBC/RC4 suites enabled by default on KitKat.
            socket.enabledCipherSuites = socket.supportedCipherSuites
        }
        return socket
    }

    companion object {
        // API 16..21 lack TLS 1.2-by-default; API 22+ already enable it.
        private val needsTls12Patch: Boolean
            get() = Build.VERSION.SDK_INT in
                Build.VERSION_CODES.JELLY_BEAN..Build.VERSION_CODES.LOLLIPOP

        /**
         * Enable TLS 1.2 on the builder for pre-Lollipop devices. If [baseFactory]/[trustManager]
         * are supplied (e.g. the trust-all pair from [UnsafeOkHttpClient]) they are wrapped;
         * otherwise the platform default trust manager is used. No-op on API 22+.
         */
        fun OkHttpClient.Builder.enableTls12(
            baseFactory: SSLSocketFactory? = null,
            trustManager: X509TrustManager? = null
        ): OkHttpClient.Builder {
            // On API 22+ the platform already negotiates TLS 1.2; only honour an explicitly
            // supplied factory (the disable-SSL path) and otherwise leave defaults untouched.
            if (!needsTls12Patch) {
                if (baseFactory != null && trustManager != null) {
                    sslSocketFactory(baseFactory, trustManager)
                }
                return this
            }
            try {
                val tm = trustManager ?: defaultTrustManager()
                val factory = baseFactory ?: SSLContext.getInstance("TLSv1.2").apply {
                    init(null, arrayOf<TrustManager>(tm), null)
                }.socketFactory
                sslSocketFactory(Tls12SocketFactory(factory), tm)

                // Don't restrict to MODERN_TLS's curated cipher list — on KitKat that can exclude
                // the only suite the device and server share. Defer to whatever the socket enables
                // (all supported suites, set above), across TLS 1.0–1.2.
                val tls12 = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                    .allEnabledCipherSuites()
                    .build()
                connectionSpecs(listOf(tls12, ConnectionSpec.CLEARTEXT))
            } catch (e: Exception) {
                Timber.e(e, "Could not enable TLS 1.2 for pre-Lollipop device")
            }
            return this
        }

        /**
         * Install a TLS 1.2-capable factory as the JVM-wide default for [HttpsURLConnection].
         * ExoPlayer's DefaultHttpDataSource uses HttpsURLConnection for video, which would
         * otherwise handshake with TLS 1.0 on KitKat. No-op on API 22+.
         */
        fun installAsDefaultHttpsFactory() {
            if (!needsTls12Patch) return
            try {
                val tm = defaultTrustManager()
                val ctx = SSLContext.getInstance("TLSv1.2")
                ctx.init(null, arrayOf<TrustManager>(tm), null)
                HttpsURLConnection.setDefaultSSLSocketFactory(Tls12SocketFactory(ctx.socketFactory))
            } catch (e: Exception) {
                Timber.e(e, "Could not install default TLS 1.2 HttpsURLConnection factory")
            }
        }

        private fun defaultTrustManager(): X509TrustManager {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }
    }
}
