package nl.giejay.android.tv.immich.weather

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoForecastService {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("temperature_unit") temperatureUnit: String,
        @Query("wind_speed_unit") windSpeedUnit: String,
        @Query("precipitation_unit") precipitationUnit: String,
        @Query("current") current: String =
            "temperature_2m,weather_code,relative_humidity_2m,precipitation,wind_speed_10m,surface_pressure",
        @Query("daily") daily: String =
            "weather_code,temperature_2m_max,temperature_2m_min,uv_index_max",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 5
    ): ForecastResponse
}

interface OpenMeteoAirQualityService {
    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "us_aqi"
    ): AirQualityResponse
}

interface OpenMeteoGeocodingService {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}
