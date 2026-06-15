package nl.giejay.android.tv.immich.shared.util

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Blurs the browse background. Downscales first (the blur radius then covers a large area of the
 * original for cheap), runs a few separable box-blur passes — which approximate a Gaussian — then
 * lets the small result scale back up. Pure Kotlin so it works on the API 19 frame without
 * RenderScript, and it only runs once per background change (debounced), not per frame.
 */
class BlurBackgroundTransformation(
    private val radius: Int = 4,
    private val downscale: Int = 3
) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val w = (toTransform.width / downscale).coerceAtLeast(1)
        val h = (toTransform.height / downscale).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(toTransform, w, h, true)
        val mutable = if (small.isMutable) small else small.copy(Bitmap.Config.ARGB_8888, true)
        if (mutable !== small) small.recycle()
        blurInPlace(mutable, radius)
        return mutable
    }

    private fun blurInPlace(bitmap: Bitmap, r: Int) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3 || r < 1) return
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxBlur(pix, tmp, w, h, r, true)
            boxBlur(tmp, pix, w, h, r, false)
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }

    /** One separable box-blur pass. [horizontal] blurs along rows, otherwise along columns. */
    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val div = 2 * r + 1
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = src[base + i.coerceIn(0, len - 1) * stride]
                sr += (c shr 16) and 0xff; sg += (c shr 8) and 0xff; sb += c and 0xff
            }
            for (x in 0 until len) {
                dst[base + x * stride] =
                    (0xff shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val cOut = src[base + (x - r).coerceIn(0, len - 1) * stride]
                val cIn = src[base + (x + r + 1).coerceIn(0, len - 1) * stride]
                sr += ((cIn shr 16) and 0xff) - ((cOut shr 16) and 0xff)
                sg += ((cIn shr 8) and 0xff) - ((cOut shr 8) and 0xff)
                sb += (cIn and 0xff) - (cOut and 0xff)
            }
        }
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
        messageDigest.update(byteArrayOf(radius.toByte(), downscale.toByte()))
    }

    override fun equals(other: Any?) =
        other is BlurBackgroundTransformation && other.radius == radius && other.downscale == downscale

    override fun hashCode() = ID.hashCode() * 31 + radius * 31 + downscale

    companion object {
        private const val ID = "nl.giejay.android.tv.immich.shared.util.BlurBackgroundTransformation"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
