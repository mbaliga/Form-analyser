package xyz.mdhv.formanalyser.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min
import xyz.mdhv.formanalyser.scoring.GrayImage

/**
 * The whole Android half of End Scan's image path: a CameraX capture in, a [GrayImage] out.
 *
 * Everything that decides *anything* about arrows lives in `core-scoring`, where it can be tested in
 * this repository. This file only moves pixels — which is also why it is worth keeping small and
 * separate: `app-android` has no SDK here and cannot be compiled, let alone run, so the less
 * judgement it carries the better.
 *
 * **Nothing here writes a file.** The photograph exists as a bitmap in memory for as long as the
 * athlete is calibrating and reviewing it, and is dropped when they leave. That is a deliberate
 * privacy choice rather than an omission: a photograph of a target is also a photograph of whoever
 * is standing near it, storing it would need a new table, a `PrivacyRegistry` classification and a
 * retention story, and nothing downstream needs the image once the candidates exist. The proposal
 * that survives carries coordinates, a confidence and a detector version — not a picture.
 */
object EndScanCapture {

    /**
     * Longest edge, in pixels, the captured photo is decoded down to.
     *
     * A modern phone hands back 12 MP, which is 48 MB as ARGB and pure waste here: the detector
     * resamples the face into a 768-pixel square regardless, so detail much past that across the
     * face is discarded in the first pass. `inSampleSize` only halves, so the decoded long edge
     * lands somewhere in 900–1800 px; even at the bottom of that range a face filling the frame is
     * still sampled more finely than the raster it is about to be resampled into.
     */
    const val MAX_LONG_EDGE: Int = 1800

    /**
     * Rows of pixels read out of the bitmap at a time in [toGray].
     *
     * Reading the whole image in one `getPixels` would allocate a second full-size buffer — 9 MB of
     * `Int`s beside a 9 MB bitmap, on a phone that has just been holding a camera preview open. A
     * band at a time costs a few dozen JNI calls and a fixed 460 KB instead.
     */
    private const val ROWS_PER_READ: Int = 64

    /**
     * A captured frame lifted out of CameraX: the compressed bytes, plus how far they have to be
     * turned to stand the way the athlete was holding the phone.
     *
     * Exists so the `ImageProxy` can be released immediately. CameraX hands out frames from a small
     * fixed pool, and holding one across a decode — or across the coroutine hop the decode wants —
     * stalls the next shutter press with no error anyone can see.
     */
    class CapturedFrame(val jpeg: ByteArray, val rotationDegrees: Int)

    /**
     * Copy one captured frame out of CameraX's buffer. Cheap enough to run on the main thread, which
     * is the point: the caller can close the `ImageProxy` on the very next line.
     *
     * Does not close [image]; the caller owns it.
     */
    fun read(image: ImageProxy): CapturedFrame? {
        val buffer = image.planes.firstOrNull()?.buffer ?: return null
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (bytes.isEmpty()) return null
        return CapturedFrame(bytes, image.imageInfo.rotationDegrees)
    }

    /**
     * Decode a captured frame into an upright bitmap, or null if it cannot be read.
     *
     * The rotation matters, and not for the reason it usually does. The athlete calibrates by
     * dragging four handles onto *this* bitmap, so any consistent orientation would give correct
     * ring scores — but the plot coordinates that come out feed group drift, and "left" and "high"
     * only mean anything if the frame is the one the athlete was standing in. So the sensor rotation
     * is applied here, once, and the same upright bitmap is what gets shown, calibrated and scanned.
     */
    fun decode(frame: CapturedFrame): Bitmap? {
        val bytes = frame.jpeg
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val rotation = frame.rotationDegrees
        if (rotation == 0) return decoded
        val rotated =
            runCatching {
                    Bitmap.createBitmap(
                        decoded,
                        0,
                        0,
                        decoded.width,
                        decoded.height,
                        Matrix().apply { postRotate(rotation.toFloat()) },
                        true,
                    )
                }
                .getOrNull() ?: return decoded
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    /** Smallest power-of-two decode divisor that brings the long edge within [MAX_LONG_EDGE]. */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / sample > MAX_LONG_EDGE) sample *= 2
        return sample
    }

    /**
     * Flatten a bitmap to the 8-bit luma plane the detector works on.
     *
     * ITU-R BT.601 weights in fixed point, which is what every other greyscale conversion on the
     * platform uses; the exact coefficients matter far less than that they are the same ones the
     * athlete's eye roughly agrees with.
     *
     * Collapsing colour is a real limitation and worth stating: a red shaft lying on the red 7-ring
     * has almost no luma contrast, and the detector will find it only by its shadow or not at all.
     * That is a missed candidate, which costs a manual entry — the failure direction this whole
     * feature is arranged around.
     */
    fun toGray(bitmap: Bitmap): GrayImage {
        val width = bitmap.width
        val height = bitmap.height
        val luma = ByteArray(width * height)
        val band = IntArray(width * ROWS_PER_READ)
        var row = 0
        while (row < height) {
            val rows = min(ROWS_PER_READ, height - row)
            bitmap.getPixels(band, 0, width, 0, row, width, rows)
            val count = width * rows
            val offset = row * width
            for (i in 0 until count) {
                val pixel = band[i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                luma[offset + i] = ((77 * r + 150 * g + 29 * b) shr 8).toByte()
            }
            row += rows
        }
        return GrayImage(width, height, luma)
    }
}
