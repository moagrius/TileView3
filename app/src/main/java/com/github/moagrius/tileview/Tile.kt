package com.github.moagrius.tileview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import com.github.moagrius.utils.Hashes

/**
 * @author Mike Dunn, 2/4/18.
 */

class Tile {

  enum class State {
    IDLE, DECODING, DECODED
  }

  companion object {
    const val TILE_SIZE = 256
  }

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
    destinationRect.right = destinationRect.left + (TILE_SIZE * sampleSize) - 20
    destinationRect.bottom = destinationRect.top + (TILE_SIZE * sampleSize) - 20
  }

  fun decode(context: Context) {
    if (state != State.IDLE) {
      return
    }
    state = State.DECODING
    updateDestinationRect()
    val canvas = Canvas(bitmap)
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
    state = State.DECODED
  }

  fun draw(canvas: Canvas) {
    canvas.drawBitmap(bitmap, null, destinationRect, null)
  }

  // TODO: we may want to actually comment this out and use memory addresses for equals, as this might be confusing things
  override fun equals(other: Any?): Boolean = other is Tile && other.startColumn == startColumn && other.startRow == startRow

  override fun hashCode() = Hashes.compute(17, 31, startColumn, startRow)

}
