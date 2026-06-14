package nl.giejay.android.tv.immich.weather

import nl.giejay.android.tv.immich.api.util.UnsafeOkHttpClient
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Fetches weather from Open-Meteo (free, no API key). Geocodes a location string to coordinates,
 * then loads the current conditions + a short daily forecast. HTTPS works on KitKat because
 * Conscrypt is installed as the security provider (see ImmichApplication).
 */
object WeatherRepository {

    // Conscrypt supplies the modern ciphers; trust-all skips KitKat's outdated CA store (the cert
    // chain can't validate against a 2014 root store). Acceptable here: Open-Meteo is public,
    // read-only, no credentials are sent.
    private val client: OkHttpClient by lazy { UnsafeOkHttpClient.unsafeOkHttpClient().build() }

    private val forecastService: OpenMeteoForecastService by lazy {
        retrofit("https://api.open-meteo.com/").create(OpenMeteoForecastService::class.java)
    }

    private val geocodingService: OpenMeteoGeocodingService by lazy {
        retrofit("https://geocoding-api.open-meteo.com/").create(OpenMeteoGeocodingService::class.java)
    }

    private val airQualityService: OpenMeteoAirQualityService by lazy {
        retrofit("https://air-quality-api.open-meteo.com/").create(OpenMeteoAirQualityService::class.java)
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Returns the weather for [location], or null if it can't be resolved/loaded. */
    suspend fun load(location: String, useFahrenheit: Boolean): Weather? {
        try {
            val geo = geocodingService.search(location).results?.firstOrNull() ?: run {
                Timber.w("No geocoding result for '$location'")
                return null
            }
            val tempUnit = if (useFahrenheit) "fahrenheit" else "celsius"
            val windUnit = if (useFahrenheit) "mph" else "km/h"
            val precipUnit = if (useFahrenheit) "in" else "mm"
            val resp = forecastService.forecast(
                geo.latitude, geo.longitude,
                temperatureUnit = tempUnit,
                windSpeedUnit = if (useFahrenheit) "mph" else "kmh",
                precipitationUnit = if (useFahrenheit) "inch" else "mm"
            )
            val current = resp.current ?: return null
            val daily = resp.daily ?: return null

            val days = daily.time.indices.map { i ->
                DailyForecast(
                    date = daily.time[i],
                    code = daily.weatherCode.getOrElse(i) { 0 },
                    high = daily.tempMax.getOrElse(i) { 0.0 }.roundToInt(),
                    low = daily.tempMin.getOrElse(i) { 0.0 }.roundToInt()
                )
            }
            // Air quality is a separate endpoint and not available everywhere — best effort.
            val aqi = try {
                airQualityService.airQuality(geo.latitude, geo.longitude).current?.usAqi
            } catch (e: Exception) {
                Timber.w(e, "Could not load air quality")
                null
            }
            val name = listOfNotNull(geo.name, geo.admin1, geo.country).distinct().firstOrNull() ?: location
            return Weather(
                locationName = name,
                currentTemp = current.temperature.roundToInt(),
                currentCode = current.weatherCode,
                unitSymbol = if (useFahrenheit) "°F" else "°C",
                days = days,
                humidity = current.humidity,
                precipitation = current.precipitation,
                precipUnit = precipUnit,
                windSpeed = current.windSpeed?.roundToInt(),
                windUnit = windUnit,
                pressureHpa = current.pressure?.roundToInt(),
                uvIndex = daily.uvIndexMax?.firstOrNull()?.roundToInt(),
                aqi = aqi
            )
        } catch (e: Exception) {
            Timber.e(e, "Could not load weather for '$location'")
            return null
        }
    }
}
