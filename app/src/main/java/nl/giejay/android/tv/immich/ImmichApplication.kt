/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.giejay.android.tv.immich

import android.app.Application
import android.content.Context
import android.os.Handler
import androidx.multidex.MultiDex
import nl.giejay.android.tv.immich.api.util.Tls12SocketFactory
import nl.giejay.android.tv.immich.sensors.ActivitySensor
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.USER_ID
import org.conscrypt.Conscrypt
import timber.log.Timber
import java.security.Security
import java.util.UUID


/**
 * Initializes libraries, such as Timber, and sets up application wide settings.
 */
class ImmichApplication : Application() {

    private val mainHandler: Handler = Handler()
    private val sensorServiceRunnable = SensorService(mainHandler)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Required on API < 21 (KitKat) to load the secondary dex files.
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        appContext = this
        // Install Conscrypt as the top security provider so KitKat can negotiate modern TLS
        // (AEAD ciphers / TLS 1.3) with current HTTPS servers — the platform stack cannot.
        // This makes the existing Tls12SocketFactory path reach modern endpoints (weather, and the
        // public Immich domain). Best-effort: fall back to the platform stack if it can't load.
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
            Timber.i("Conscrypt installed as security provider")
        } catch (e: Throwable) {
            Timber.e(e, "Could not install Conscrypt; falling back to platform TLS")
        }
        // Enable TLS 1.2 for ExoPlayer's HttpsURLConnection-based video data source on KitKat.
        Tls12SocketFactory.installAsDefaultHttpsFactory()
        PreferenceManager.init(this)
        activitySensor = ActivitySensor(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        var userId = PreferenceManager.get(USER_ID)
        if(userId.isBlank()){
            userId = UUID.randomUUID().toString()
            PreferenceManager.save(USER_ID, userId)
        }

        // Start polling the (Nixplay) motion sensor to drive the display wakelock.
        this.mainHandler.post(sensorServiceRunnable)
    }

    companion object {
        var appContext: Context? = null

        var activitySensor: ActivitySensor? = null
    }

    class SensorService internal constructor(val handler: Handler? = null) : Runnable {
        override fun run() {
            handler?.removeCallbacks(this)
            activitySensor?.checkSensors()
            handler?.postDelayed(this, 1000L)
        }
    }
}
