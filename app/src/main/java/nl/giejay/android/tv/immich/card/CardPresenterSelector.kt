package nl.giejay.android.tv.immich.card

import android.content.Context
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector

class CardPresenterSelector(val context: Context): PresenterSelector() {
    private val default by lazy { CardPresenter(context) }
    private val imageOnly by lazy { ImageOnlyCardPresenter(context) }

    override fun getPresenter(item: Any?): Presenter {
        return if ((item as? ICard)?.imageOnly == true) imageOnly else default
    }
}