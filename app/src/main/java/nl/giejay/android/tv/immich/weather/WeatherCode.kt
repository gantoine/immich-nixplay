package nl.giejay.android.tv.immich.weather

import nl.giejay.android.tv.immich.R

/**
 * Maps WMO weather interpretation codes (as returned by Open-Meteo) to a short description and an
 * icon drawable. https://open-meteo.com/en/docs ("WMO Weather interpretation codes").
 */
object WeatherCode {

    fun description(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> "—"
    }

    /** Maps a WMO code to the animated background scene to play behind the weather page. */
    fun scene(code: Int): WeatherAnimationView.Scene = when (code) {
        0, 1 -> WeatherAnimationView.Scene.CLEAR
        2, 3 -> WeatherAnimationView.Scene.CLOUDS
        45, 48 -> WeatherAnimationView.Scene.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherAnimationView.Scene.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherAnimationView.Scene.SNOW
        95, 96, 99 -> WeatherAnimationView.Scene.THUNDER
        else -> WeatherAnimationView.Scene.CLOUDS
    }

    fun iconRes(code: Int): Int = when (code) {
        0, 1 -> R.drawable.ic_weather_sun
        2 -> R.drawable.ic_weather_partly
        3, 45, 48 -> R.drawable.ic_weather_cloud
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
        95, 96, 99 -> R.drawable.ic_weather_thunder
        else -> R.drawable.ic_weather_cloud
    }
}
