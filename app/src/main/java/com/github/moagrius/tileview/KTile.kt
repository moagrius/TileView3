package com.github.moagrius.tileview

import android.content.Context
import android.graphics.*
import android.util.Log
import com.github.moagrius.utils.Hashes

/**
 * @author Mike Dunn, 2/4/18.
 */

class KTile {

  enum class State {
    IDLE, DECODING, DECODED
  }

  companion object {
    const val TILE_SIZE = 256
  }

  val cacheKey:String
    get() = "$startRow:$startColumn:$sampleSize"

  val sampleSize:Int
    get() = options?.inSampleSize ?: 1

  var startRow = 0
  var startColumn = 0

  var options: BitmapFactory.Options? = null

  private var bitmap: Bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565)
  private var state = State.IDLE

  private val destinationRect = Rect()

  private fun updateDestinationRect() {
    destinationRect.left = startColumn * TILE_SIZE
    destinationRect.top = startRow * TILE_SIZE
    destinationRect.right = destinationRect.left + (TILE_SIZE * sampleSize)
    destinationRect.bottom = destinationRect.top + (TILE_SIZE * sampleSize)
  }

  fun decode(context: Context, cache: Cache) {
    if (state != State.IDLE) {
      return
    }
    state = State.DECODING
    updateDestinationRect()
    val cached = cache.get(cacheKey)
    cached?.let {
      Log.d("T", "got bitmap from memory cache")
      bitmap = cached
      state = State.DECODED
      return
    }
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.GREEN)
    val size = TILE_SIZE / sampleSize.toFloat()
    for (i in 0 until sampleSize) {
      for (j in 0 until sampleSize) {
        Log.d("G", "iterating i:$i, j:$j")
        val r = startRow + i
        val c = startColumn + j
        val file = "tiles/phi-500000-${c}_$r.jpg"
        val stream = context.assets.open(file)
        stream?.let {
          val piece = BitmapFactory.decodeStream(stream, null, options)
          val left = j * size
          val top = i * size
          Log.d("G", "putting piece for $startRow:$startColumn at $left:$top")
          canvas.drawBitmap(piece, left, top, null)
        }
      }
    }
    cache.put(cacheKey, bitmap)
    state = State.DECODED
  }

  fun draw(canvas: Canvas) {
    canvas.drawBitmap(bitmap, null, destinationRect, null)
  }

  // TODO: we may want to actually comment this out and use memory addresses for equals, as this might be confusing things
  override fun equals(other: Any?): Boolean = other is KTile && other.startColumn == startColumn && other.startRow == startRow && other.sampleSize == sampleSize
  override fun hashCode() = Hashes.compute(17, 31, startColumn, startRow)

  interface Cache {
    fun get(key:Any):Bitmap?
    fun put(key:Any, bitmap:Bitmap):Bitmap?
  }

}
