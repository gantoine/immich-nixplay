package nl.giejay.mediaslider.transformations

import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import com.bumptech.glide.request.target.Target
import java.security.MessageDigest

/**
 * Crops (fills) images whose orientation matches the target slot, and leaves opposite-orientation
 * images untouched so the view's centerInside scaleType letterboxes them. Orientation is compared
 * against the real target slot (outWidth/outHeight), which correctly handles display rotation and
 * the merged-portrait half-width slots; screen metrics are only used as a fallback.
 */
class OrientationAwareCenterCrop(context: Context,
                                 private val transformResult: (String) -> Unit) : BitmapTransformation() {
    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        // toTransform is already EXIF-rotated by Glide's Downsampler, so its dimensions reflect the
        // visually-correct orientation.
        val imageIsLandscape = toTransform.width >= toTransform.height

        val validTarget = outWidth > 0 && outHeight > 0 &&
            outWidth != Target.SIZE_ORIGINAL && outHeight != Target.SIZE_ORIGINAL
        val slotWidth = if (validTarget) outWidth else screenWidth
        val slotHeight = if (validTarget) outHeight else screenHeight
        val slotIsLandscape = slotWidth >= slotHeight

        return if (imageIsLandscape == slotIsLandscape) {
            transformResult("Orientation matches slot, cropping to fill")
            TransformationUtils.centerCrop(pool, toTransform, outWidth, outHeight)
        } else {
            transformResult("Orientation differs from slot, letterboxing")
            toTransform
        }
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun equals(other: Any?): Boolean = other is OrientationAwareCenterCrop

    override fun hashCode(): Int = ID.hashCode()

    companion object {
        private const val ID = "nl.giejay.mediaslider.transformations.OrientationAwareCenterCrop"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
