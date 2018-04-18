package com.github.moagrius.tileview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.LruCache
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

    //https://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inTempStorage
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
      destinationRect.top = row * TILE_SIZE
      destinationRect.bottom = destinationRect.top + TILE_SIZE
    }
  var column = 0
    set(column) {
      field = column
      destinationRect.left = column * TILE_SIZE
      destinationRect.right = destinationRect.left + TILE_SIZE
    }

  var sample = 1

  private val destinationRect = Rect()

  private val reusableBitmaps: MutableSet<SoftReference<Bitmap>> = Collections.synchronizedSet(HashSet())

  fun decode(context: Context, cache: LruCache<String, Bitmap>) {
    if (state != State.IDLE) {
      return
    }
    state = State.DECODING
    val formattedFileName = "tiles/phi-500000-${column}_$row.jpg"
    val memoryCacheKey = formattedFileName + sample
    bitmap = cache[memoryCacheKey]
    if (bitmap != null) {
      Log.d("T", "got bitmap from memory cache")
      state = State.DECODED
      return
    }
    val assetManager = context.assets
    try {
      val inputStream = assetManager.open(formattedFileName)
      if (inputStream != null) {
        try {
          val options = BitmapFactory.Options()
          options.inTempStorage = decodeBuffer
          options.inPreferredConfig = Bitmap.Config.RGB_565
          options.inSampleSize = sample
          Log.d("T", "sample: $sample")
          addInBitmapOptions(options)
          bitmap = BitmapFactory.decodeStream(inputStream, null, options)
          cache.put(memoryCacheKey, bitmap)
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

  private fun addInBitmapOptions(options: BitmapFactory.Options) {
    // inBitmap only works with mutable bitmaps, so force the decoder to return mutable bitmaps.
    options.inMutable = true
    // Try to find a bitmap to use for inBitmap.
    val inBitmap = getBitmapFromReusableSet(options)
    if (inBitmap != null) {
      // If a suitable bitmap has been found, set it as the value of inBitmap.
      options.inBitmap = inBitmap
    }
  }

  // This method iterates through the reusable bitmaps, looking for one to use for inBitmap
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

  fun draw(canvas: Canvas) {
    bitmap?.let {
      canvas.drawBitmap(bitmap, null, destinationRect, null)
    }
  }

  interface Cache {
    fun get(key:Any):Bitmap
    fun put(key:Any, bitmap:Bitmap)
  }

}
