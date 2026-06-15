package nl.giejay.android.tv.immich.weather

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.WEATHER_ANIMATED_BACKGROUND
import nl.giejay.android.tv.immich.shared.prefs.WEATHER_LOCATION
import nl.giejay.android.tv.immich.shared.prefs.WEATHER_USE_FAHRENHEIT
import java.text.SimpleDateFormat
import java.util.Locale

class WeatherFragment : Fragment(), BrowseSupportFragment.MainFragmentAdapterProvider {

    // Required so this fragment can be hosted as a page row inside HomeFragment's BrowseSupportFragment.
    private val mainFragmentAdapter = BrowseSupportFragment.MainFragmentAdapter(this)

    override fun getMainFragmentAdapter() = mainFragmentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_weather, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainFragmentAdapter.fragmentHost?.notifyDataReady(mainFragmentAdapter)
        val status = view.findViewById<TextView>(R.id.weather_status)
        val content = view.findViewById<View>(R.id.weather_content)

        val location = PreferenceManager.get(WEATHER_LOCATION).trim()
        if (location.isBlank()) {
            content.visibility = View.GONE
            status.visibility = View.VISIBLE
            status.text = getString(R.string.weather_set_location)
            return
        }

        content.visibility = View.GONE
        status.visibility = View.VISIBLE
        status.text = getString(R.string.weather_loading)

        val fahrenheit = PreferenceManager.get(WEATHER_USE_FAHRENHEIT)
        viewLifecycleOwner.lifecycleScope.launch {
            val weather = WeatherRepository.load(location, fahrenheit)
            if (!isAdded) return@launch
            if (weather == null) {
                status.visibility = View.VISIBLE
                status.text = getString(R.string.weather_error)
                content.visibility = View.GONE
            } else {
                status.visibility = View.GONE
                content.visibility = View.VISIBLE
                bind(view, weather)
            }
        }
    }

    private fun bind(view: View, weather: Weather) {
        val scene = WeatherCode.scene(weather.currentCode)
        view.background = backgroundFor(scene)

        val animation = view.findViewById<WeatherAnimationView>(R.id.weather_animation)
        animation.setScene(
            if (PreferenceManager.get(WEATHER_ANIMATED_BACKGROUND)) scene
            else WeatherAnimationView.Scene.NONE
        )

        view.findViewById<TextView>(R.id.weather_location).text = weather.locationName
        view.findViewById<TextView>(R.id.current_temp).text = "${weather.currentTemp}${weather.unitSymbol}"
        view.findViewById<TextView>(R.id.current_condition).text = WeatherCode.description(weather.currentCode)
        view.findViewById<ImageView>(R.id.current_icon)
            .setImageDrawable(AppCompatResources.getDrawable(requireContext(), WeatherCode.iconRes(weather.currentCode)))
        weather.days.firstOrNull()?.let { today ->
            view.findViewById<TextView>(R.id.current_hilo).text = "H ${today.high}°  L ${today.low}°"
        }

        val inflater = LayoutInflater.from(requireContext())

        val statsLeft = view.findViewById<LinearLayout>(R.id.stats_left)
        val statsRight = view.findViewById<LinearLayout>(R.id.stats_right)
        statsLeft.removeAllViews()
        statsRight.removeAllViews()
        val statGap = (72 * resources.displayMetrics.density).toInt()
        fun addStat(column: LinearLayout, value: String?, label: String, level: String? = null, levelColor: Int = 0) {
            if (value == null) return
            val item = inflater.inflate(R.layout.weather_stat, column, false)
            item.findViewById<TextView>(R.id.stat_value).text = value
            item.findViewById<TextView>(R.id.stat_label).text = label
            if (level != null) {
                item.findViewById<TextView>(R.id.stat_level).apply {
                    text = level
                    setTextColor(levelColor)
                    visibility = View.VISIBLE
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (column.childCount > 0) lp.topMargin = statGap
            item.layoutParams = lp
            column.addView(item)
        }
        addStat(statsLeft, weather.humidity?.let { "$it%" }, getString(R.string.weather_humidity))
        addStat(statsLeft, weather.precipitation?.let { "$it ${weather.precipUnit}" }, getString(R.string.weather_precipitation))
        weather.uvIndex?.let { uv ->
            val (level, color) = uvLevel(uv)
            addStat(statsRight, "$uv", getString(R.string.weather_uv), level, color)
        }
        weather.aqi?.let { aqi ->
            val (level, color) = aqiLevel(aqi)
            addStat(statsRight, "$aqi", getString(R.string.weather_aqi), level, color)
        }

        val row = view.findViewById<LinearLayout>(R.id.forecast_row)
        row.removeAllViews()
        weather.days.forEachIndexed { index, day ->
            val item = inflater.inflate(R.layout.weather_day_item, row, false)
            item.findViewById<TextView>(R.id.day_name).text =
                if (index == 0) getString(R.string.weather_today) else dayName(day.date)
            item.findViewById<ImageView>(R.id.day_icon)
                .setImageDrawable(AppCompatResources.getDrawable(requireContext(), WeatherCode.iconRes(day.code)))
            item.findViewById<TextView>(R.id.day_hilo).text = "${day.high}° / ${day.low}°"
            row.addView(item)
        }
    }

    /** A vertical gradient tinted to the current conditions (warm for clear, cool for cloud/rain). */
    private fun backgroundFor(scene: WeatherAnimationView.Scene): GradientDrawable {
        val colors = when (scene) {
            WeatherAnimationView.Scene.CLEAR -> intArrayOf(0xFF6B3B28.toInt(), 0xFFB06A40.toInt())
            WeatherAnimationView.Scene.CLOUDS -> intArrayOf(0xFF2A313C.toInt(), 0xFF454F5E.toInt())
            WeatherAnimationView.Scene.RAIN -> intArrayOf(0xFF1F2832.toInt(), 0xFF354554.toInt())
            WeatherAnimationView.Scene.SNOW -> intArrayOf(0xFF2E3742.toInt(), 0xFF4C5965.toInt())
            WeatherAnimationView.Scene.FOG -> intArrayOf(0xFF2B2E33.toInt(), 0xFF474B52.toInt())
            WeatherAnimationView.Scene.THUNDER -> intArrayOf(0xFF181B22.toInt(), 0xFF2C313D.toInt())
            else -> intArrayOf(0xFF14161A.toInt(), 0xFF24272E.toInt())
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
    }

    /** UV index -> (label, color) on the WHO scale. */
    private fun uvLevel(uv: Int): Pair<String, Int> = when {
        uv <= 2 -> "Low" to 0xFF66BB6A.toInt()
        uv <= 5 -> "Moderate" to 0xFFFFCA28.toInt()
        uv <= 7 -> "High" to 0xFFFFA726.toInt()
        uv <= 10 -> "Very high" to 0xFFEF5350.toInt()
        else -> "Extreme" to 0xFFAB47BC.toInt()
    }

    /** US AQI -> (label, color). */
    private fun aqiLevel(aqi: Int): Pair<String, Int> = when {
        aqi <= 50 -> "Good" to 0xFF66BB6A.toInt()
        aqi <= 100 -> "Moderate" to 0xFFFFCA28.toInt()
        aqi <= 150 -> "Poor" to 0xFFFFA726.toInt()
        aqi <= 200 -> "Unhealthy" to 0xFFEF5350.toInt()
        aqi <= 300 -> "Very poor" to 0xFFAB47BC.toInt()
        else -> "Hazardous" to 0xFF8D6E63.toInt()
    }

    private fun dayName(isoDate: String): String = try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
        if (date != null) SimpleDateFormat("EEE", Locale.getDefault()).format(date) else isoDate
    } catch (e: Exception) {
        isoDate
    }
}
