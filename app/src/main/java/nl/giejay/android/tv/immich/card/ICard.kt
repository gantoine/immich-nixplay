package nl.giejay.android.tv.immich.card

interface ICard {
    val title: String
    val description: String?
    val id: String
    val thumbnailUrl: String?
    val backgroundUrl: String?
    val selected: Boolean
    // Render as image-only (no info bar) — used for photo/video previews.
    val imageOnly: Boolean get() = false
}