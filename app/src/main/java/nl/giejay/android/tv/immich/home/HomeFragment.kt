package nl.giejay.android.tv.immich.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DividerPresenter
import androidx.leanback.widget.DividerRow
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.PageRow
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.SectionRow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumFragment
import nl.giejay.android.tv.immich.assets.AllAssetFragment
import nl.giejay.android.tv.immich.assets.FolderFragment
import nl.giejay.android.tv.immich.assets.RandomAssetsFragment
import nl.giejay.android.tv.immich.assets.RecentAssetsFragment
import nl.giejay.android.tv.immich.assets.SimilarTimeAssetsFragment
import nl.giejay.android.tv.immich.people.PeopleFragment
import nl.giejay.android.tv.immich.settings.SettingsFragment
import nl.giejay.android.tv.immich.shared.fragment.GridFragment
import nl.giejay.android.tv.immich.shared.prefs.HIDDEN_HOME_ITEMS
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.viewmodel.KeyEventsViewModel
import nl.giejay.android.tv.immich.weather.WeatherFragment
import timber.log.Timber

class HomeFragment : BrowseSupportFragment() {
    private lateinit var mRowsAdapter: ArrayObjectAdapter
    private lateinit var rows: List<PageRow>
    val immichRowPresenter = ImmichRowPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("Loaded Home")

        setupUi()
        loadData()

        mainFragmentRegistry.registerFragment(PageRow::class.java, PageRowFragmentFactory())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headersSupportFragment.setOnHeaderViewSelectedListener { _, row ->
            title = row?.headerItem?.name ?: "-"
            selectedPosition = row?.let { mRowsAdapter.indexOf(it) } ?: 0
        }

        // Defer past BrowseSupportFragment's own setup: it wires its internal header-click handler
        // onto the headers fragment after onViewCreated (overwriting ours), and that handler
        // early-returns for PageRows so the Edit toggle never fires. Setting ours last wins.
        view.post {
        headersSupportFragment.setOnHeaderClickedListener { _, row ->
            if (row.headerItem.name == getString(R.string.edit)) {
                immichRowPresenter.editMode = !immichRowPresenter.editMode
                if(immichRowPresenter.editMode){
                    mRowsAdapter.clear()
                    mRowsAdapter.addAll(0, rows.filter { it.headerItem.name != getString(R.string.settings) })
                } else {
                    mRowsAdapter.clear();
                    mRowsAdapter.addAll(0, rows.filter { !PreferenceManager.itemInStringSet(it.headerItem.name, HIDDEN_HOME_ITEMS) })
                }
                adapter.notifyItemRangeChanged(0, mRowsAdapter.size());
            } else if(immichRowPresenter.editMode){
                PreferenceManager.toggleStringSetItem(row.headerItem.name, HIDDEN_HOME_ITEMS)
                adapter.notifyItemRangeChanged(0, mRowsAdapter.size())
            } else{
                if (!this.isInHeadersTransition) {
                    this.startHeadersTransition(false)
//                    this.mainFragment.requireView().requestFocus()
                }
            }
        }
        }

        // Jump straight to the Settings row when the remote's SETTINGS button is pressed.
        val keyEvents = ViewModelProvider(requireActivity())[KeyEventsViewModel::class.java]
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                keyEvents.state.collect { event ->
                    // The frame's Settings button sends scancode 141 (KEY_SETUP), which is
                    // unmapped in the keylayout so its keyCode is UNKNOWN — match the scancode too.
                    if (event != null && event.action == KeyEvent.ACTION_DOWN &&
                        (event.keyCode == KeyEvent.KEYCODE_SETTINGS || event.scanCode == KEY_SETUP_SCANCODE)) {
                        jumpToSettings()
                    }
                }
            }
        }
    }

    private fun jumpToSettings() {
        val settingsName = getString(R.string.settings)
        val index = (0 until mRowsAdapter.size()).firstOrNull {
            (mRowsAdapter.get(it) as? Row)?.headerItem?.name == settingsName
        } ?: return
        if (immichRowPresenter.editMode) {
            // leave edit mode so the normal Settings row is present
            immichRowPresenter.editMode = false
            mRowsAdapter.clear()
            mRowsAdapter.addAll(0, rows.filter { !PreferenceManager.itemInStringSet(it.headerItem.name, HIDDEN_HOME_ITEMS) })
            adapter.notifyItemRangeChanged(0, mRowsAdapter.size())
        }
        setSelectedPosition(index)
        // setSelectedPosition only selects the header; move focus into the Settings cards too.
        view?.post {
            if (!isInHeadersTransition) {
                startHeadersTransition(false)
            }
        }
    }

    private fun setupUi() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        // Translucent sidebar so the photo behind shows through (this colors the headers dock).
        brandColor = android.graphics.Color.parseColor("#99000000")
        title = getString(R.string.albums)
//        setOnSearchClickedListener {
//            Toast.makeText(
//                activity, "Search!", Toast.LENGTH_SHORT
//            ).show()
//        }

        // Lines of code to be added
        val sHeaderPresenter: PresenterSelector = ClassPresenterSelector()
            .addClassPresenter(DividerRow::class.java, DividerPresenter())
            .addClassPresenter(
                SectionRow::class.java,
                RowHeaderPresenter()
            )
            .addClassPresenter(Row::class.java, immichRowPresenter)

        setHeaderPresenterSelector(sHeaderPresenter)
    }

    private fun loadData() {
        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = mRowsAdapter
        rows = createRows()
        mRowsAdapter.addAll(0, rows.filter { !PreferenceManager.itemInStringSet(it.headerItem.name, HIDDEN_HOME_ITEMS) })
    }

    private fun createRows(): List<PageRow> {
        return HEADERS.mapIndexed { index, header -> PageRow(HeaderItem(index.toLong(), header.name)) }
    }

    private class PageRowFragmentFactory : FragmentFactory<Fragment>() {
        override fun createFragment(rowObj: Any): Fragment {
            val row = rowObj as Row
            Timber.i("Going to show page: ${row.headerItem.name}")
            return HEADERS[row.headerItem.id.toInt()].fragment()
        }
    }

    companion object {
        // Hardware scancode for the frame remote's Settings button (KEY_SETUP, unmapped in keylayout).
        private const val KEY_SETUP_SCANCODE = 141

        private val HEADERS: List<Header> = listOf(
            Header(ImmichApplication.appContext!!.getString(R.string.albums)) {
                AlbumFragment().apply {
                    arguments = bundleOf("selectionMode" to false)
                }
            },
            Header(ImmichApplication.appContext!!.getString(R.string.photos)) { AllAssetFragment() },
            Header(ImmichApplication.appContext!!.getString(R.string.random)) { RandomAssetsFragment() },
            Header(ImmichApplication.appContext!!.getString(R.string.people)) { PeopleFragment() },
            Header(ImmichApplication.appContext!!.getString(R.string.recent)) { RecentAssetsFragment() },
            Header(ImmichApplication.appContext!!.getString(R.string.seasonal)) { SimilarTimeAssetsFragment() },
            Header(ImmichApplication.appContext!!.getString(R.string.folders)) { FolderFragment() },
            Header(ImmichApplication.appContext!!.getString(R.string.weather)) { WeatherFragment() },
            Header(ImmichApplication.appContext!!.getString(R.string.edit)) { GridFragment(hideProgressBar = true) },
            Header(ImmichApplication.appContext!!.getString(R.string.settings)) { SettingsFragment() },
        )
    }
}

class Header(val name: String, var show: Boolean = false, val fragment: () -> Fragment)
