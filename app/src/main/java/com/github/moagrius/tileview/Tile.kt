package com.github.moagrius.tileview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import com.github.moagrius.utils.Hashes
import java.lang.ref.SoftReference
import java.util.*

/**
 * @author Mike Dunn, 2/4/18.
 */

class Tile {

  enum class State {
    IDLE, DECODING, DECODED
  }

  companion object {

    const val TILE_SIZE = 256

    private val decodeBuffer = ByteArray(16 * 1024)
    private val bitmapOptions = BitmapFactory.Options()

    init {
      bitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565
      bitmapOptions.inTempStorage = ByteArray(16 * 1024)
    }

  }

  var bitmap: Bitmap? = null
  var state = State.IDLE

  var row = 0
    set(row) {
      field = row
      top = (row * TILE_SIZE).toFloat()
      bottom = top + TILE_SIZE
    }
  var column = 0
    set(column) {
      field = column
      left = (column * TILE_SIZE).toFloat()
      right = left + TILE_SIZE
    }

  var left = 0F
  var top = 0F
  var right = 0F
  var bottom = 0F

  var scale = 0F
  var sample = 1

  private val sourceRect = Rect(0, 0, TILE_SIZE, TILE_SIZE)
  private val destinationRect = Rect()

  private var reusableBitmaps: MutableSet<SoftReference<Bitmap>> = Collections.synchronizedSet(HashSet())

  fun updateDestinationRect() {
    //Log.d("T", "left:$left, scale:$scale")
    destinationRect.left = (left / scale).toInt()
    destinationRect.top = (top / scale).toInt()
    destinationRect.right = (right / scale).toInt()
    destinationRect.bottom = (bottom / scale).toInt()
  }

  fun decode(context: Context) {
    if (state != State.IDLE) {
      return
    }
    state = State.DECODING
    val formattedFileName = "tiles/phi-500000-${column}_$row.jpg"
    val assetManager = context.assets
    try {
      val inputStream = assetManager.open(formattedFileName)
      if (inputStream != null) {
        try {
          val options = BitmapFactory.Options()
          options.inTempStorage = decodeBuffer
          options.inPreferredConfig = Bitmap.Config.RGB_565
          options.inSampleSize = sample
          //addInBitmapOptions(options)
          bitmap = BitmapFactory.decodeStream(inputStream, null, bitmapOptions)
          state = State.DECODED
        } catch (e: OutOfMemoryError) {
          Log.d("T", "OOME")
          // this is probably an out of memory error - you can try sleeping (this method won't be called in the UI thread) or try again (or give up)
        } catch (e: Exception) {
          // unknown
        }
      }
    } catch (e: Exception) {
      Log.d("T", "IOException in decode, probably can't find file")
      // this is probably an IOException, meaning the file can't be found
    }

  }

  fun clear() {
    bitmap = null
    state = State.IDLE
  }

  override fun equals(other: Any?): Boolean {
    if (other is Tile) {
      return other.column == column && other.row == row
    }
    return false
  }

  override fun hashCode() = Hashes.compute(17, 31, column, row)

  /*
  private fun addInBitmapOptions(options: BitmapFactory.Options) {
    // inBitmap only works with mutable bitmaps, so force the decoder to
    // return mutable bitmaps.
    options.inMutable = true


    // Try to find a bitmap to use for inBitmap.
    val inBitmap = getBitmapFromReusableSet(options)

    if (inBitmap != null) {
      // If a suitable bitmap has been found, set it as the value of
      // inBitmap.
      options.inBitmap = inBitmap
    }

  }

  // This method iterates through the reusable bitmaps, looking for one
  // to use for inBitmap:
  private fun getBitmapFromReusableSet(options: BitmapFactory.Options): Bitmap? {
    var bitmap: Bitmap? = null
    if (!reusableBitmaps.isEmpty()) {
      val iterator = reusableBitmaps.iterator()
      while (iterator.hasNext()) {
        bitmap = iterator.next().get()
        if (bitmap == null) {
          iterator.remove()
        } else if (bitmap.isMutable) {
          // Check to see it the item can be used for inBitmap.
          if (canUseForInBitmap(bitmap, options)) {
            // Remove from reusable set so it can't be used again.
            iterator.remove()
            break
          }
        }
      }
    }
    return bitmap
  }

  private fun canUseForInBitmap(candidate: Bitmap, targetOptions: BitmapFactory.Options): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      // From Android 4.4 (KitKat) onward we can re-use if the byte size of
      // the new bitmap is smaller than the reusable bitmap candidate
      // allocation byte count.
      val width = targetOptions.outWidth / targetOptions.inSampleSize
      val height = targetOptions.outHeight / targetOptions.inSampleSize
      val byteCount = width * height * getBytesPerPixel(candidate.config)
      byteCount <= candidate.allocationByteCount
    } else {
      // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
      (targetOptions.inSampleSize == 1
          && candidate.width == targetOptions.outWidth
          && candidate.height == targetOptions.outHeight)
    }
  }

  private fun getBytesPerPixel(config: Bitmap.Config): Int {
    return when (config) {
      Bitmap.Config.ARGB_8888 -> 4
      Bitmap.Config.RGB_565 -> 2
      Bitmap.Config.ARGB_4444 -> 2
      else -> 1
    }
  }

  */

  fun draw(canvas: Canvas) {
    bitmap?.let {
      //Log.d("T", "drawing bitmap at $destinationRect")
      canvas.drawBitmap(bitmap, sourceRect, destinationRect, null)
    }
  }

}
