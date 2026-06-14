package nl.giejay.android.tv.immich.api

import android.util.Log
import nl.giejay.android.tv.immich.api.util.Tls12SocketFactory.Companion.enableTls12
import nl.giejay.android.tv.immich.api.util.UnsafeOkHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object ApiClientFactory {

    fun getClient(disableSsl: Boolean, apiKey: String, debugMode: Boolean): OkHttpClient {
        val apiKeyInterceptor = interceptor(apiKey)
        // The unsafe builder already enables TLS 1.2 (via its trust-all factory); the secure
        // path needs it too so HTTPS works on KitKat (API 19), where TLS 1.2 is off by default.
        val builder = if (disableSsl)
            UnsafeOkHttpClient.unsafeOkHttpClient()
        else OkHttpClient.Builder().enableTls12()
        builder.addInterceptor(apiKeyInterceptor)
        if (debugMode) {
            // Logs request/response lines and "HTTP FAILED: <exception>" to logcat (tag ImmixHttp),
            // visible even in release where Timber is not planted.
            val logging = HttpLoggingInterceptor { msg -> Log.i("ImmixHttp", msg) }
            logging.level = HttpLoggingInterceptor.Level.BASIC
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    private fun interceptor(apiKey: String): Interceptor = Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
            .addHeader("x-api-key", apiKey.trim())
            .build()
        chain.proceed(newRequest)
    }
}
