package nl.giejay.android.tv.immich.weather

import com.google.gson.annotations.SerializedName

/** Open-Meteo geocoding response (city name -> coordinates). */
data class GeocodingResponse(val results: List<GeoResult>?)

data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?
)

/** Open-Meteo forecast response. */
data class ForecastResponse(val current: CurrentWeather?, val daily: DailyWeather?)

data class CurrentWeather(
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("relative_humidity_2m") val humidity: Int? = null,
    @SerializedName("precipitation") val precipitation: Double? = null,
    @SerializedName("wind_speed_10m") val windSpeed: Double? = null,
    @SerializedName("surface_pressure") val pressure: Double? = null
)

data class DailyWeather(
    val time: List<String>,
    @SerializedName("weather_code") val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max") val tempMax: List<Double>,
    @SerializedName("temperature_2m_min") val tempMin: List<Double>,
    @SerializedName("uv_index_max") val uvIndexMax: List<Double>? = null
)

/** Open-Meteo air-quality response (separate host). */
data class AirQualityResponse(val current: AirQualityCurrent?)

data class AirQualityCurrent(@SerializedName("us_aqi") val usAqi: Int?)

/** Domain model handed to the UI. */
data class Weather(
    val locationName: String,
    val currentTemp: Int,
    val currentCode: Int,
    val unitSymbol: String,
    val days: List<DailyForecast>,
    val humidity: Int?,
    val precipitation: Double?,
    val precipUnit: String,
    val windSpeed: Int?,
    val windUnit: String,
    val pressureHpa: Int?,
    val uvIndex: Int?,
    val aqi: Int?
)

data class DailyForecast(
    val date: String, // ISO yyyy-MM-dd
    val code: Int,
    val high: Int,
    val low: Int
)
