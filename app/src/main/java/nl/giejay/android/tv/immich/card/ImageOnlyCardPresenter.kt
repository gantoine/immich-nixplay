package nl.giejay.android.tv.immich.card

import android.content.Context
import nl.giejay.android.tv.immich.R

/**
 * Card presenter for photo/video previews: a mainOnly (image-only) ImageCardView with no info bar.
 * Directly hiding the info view doesn't stick — BaseCardView re-applies the info region from the
 * card type — so the type itself is set to mainOnly via the theme.
 */
class ImageOnlyCardPresenter(context: Context) :
    CardPresenter(context, R.style.ImageOnlyCardTheme)
