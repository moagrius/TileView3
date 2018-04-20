package com.github.moagrius.tileview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
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

  var row = 0
  var column = 0

  var options: BitmapFactory.Options? = null

  private var bitmap: Bitmap? = null
  private var state = State.IDLE

  private val destinationRect = Rect()

  private fun updateDestinationRect() {
    val sample = options?.inSampleSize ?: 1
    destinationRect.left = column * TILE_SIZE
    destinationRect.top = row * TILE_SIZE
    destinationRect.right = destinationRect.left + (TILE_SIZE * sample) - 20
    destinationRect.bottom = destinationRect.top + (TILE_SIZE * sample) - 20
  }

  fun decode(context: Context) {
    if (state != State.IDLE) {
      return
    }
    state = State.DECODING
    updateDestinationRect()
    val file = if (options?.inSampleSize == 2) {
      "tiles/phi-250000-${column/2}_${row/2}.jpg"
    } else {
      "tiles/phi-500000-${column}_$row.jpg"
    }
    val stream = context.assets.open(file)
    stream?.let {
      bitmap = BitmapFactory.decodeStream(stream, null, options)
      state = State.DECODED
    }
  }

  fun draw(canvas: Canvas) {
    bitmap?.let {
      canvas.drawBitmap(bitmap, null, destinationRect, null)
    }
  }

  // TODO: we may want to actually comment this out and use memory addresses for equals, as this might be confusing things
  override fun equals(other: Any?): Boolean = other is Tile && other.column == column && other.row == row

  override fun hashCode() = Hashes.compute(17, 31, column, row)

}
