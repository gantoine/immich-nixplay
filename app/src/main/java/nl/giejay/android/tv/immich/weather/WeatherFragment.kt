package nl.giejay.android.tv.immich.weather

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
        view.findViewById<TextView>(R.id.weather_location).text = weather.locationName
        view.findViewById<TextView>(R.id.current_temp).text = "${weather.currentTemp}${weather.unitSymbol}"
        view.findViewById<TextView>(R.id.current_condition).text = WeatherCode.description(weather.currentCode)
        view.findViewById<ImageView>(R.id.current_icon)
            .setImageDrawable(AppCompatResources.getDrawable(requireContext(), WeatherCode.iconRes(weather.currentCode)))
        weather.days.firstOrNull()?.let { today ->
            view.findViewById<TextView>(R.id.current_hilo).text = "H ${today.high}°  L ${today.low}°"
        }

        val inflater = LayoutInflater.from(requireContext())

        val stats = view.findViewById<LinearLayout>(R.id.stats_row)
        stats.removeAllViews()
        fun addStat(value: String?, label: String) {
            if (value == null) return
            val item = inflater.inflate(R.layout.weather_stat_item, stats, false)
            item.findViewById<TextView>(R.id.stat_value).text = value
            item.findViewById<TextView>(R.id.stat_label).text = label
            stats.addView(item)
        }
        addStat(weather.precipitation?.let { "$it ${weather.precipUnit}" }, getString(R.string.weather_precipitation))
        addStat(weather.windSpeed?.let { "$it ${weather.windUnit}" }, getString(R.string.weather_wind))
        addStat(weather.humidity?.let { "$it%" }, getString(R.string.weather_humidity))
        addStat(weather.uvIndex?.let { "$it" }, getString(R.string.weather_uv))
        addStat(weather.pressureHpa?.let { "$it hPa" }, getString(R.string.weather_pressure))
        addStat(weather.aqi?.let { "$it" }, getString(R.string.weather_aqi))

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

    private fun dayName(isoDate: String): String = try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
        if (date != null) SimpleDateFormat("EEE", Locale.getDefault()).format(date) else isoDate
    } catch (e: Exception) {
        isoDate
    }
}
