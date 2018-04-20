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
    destinationRect.left = column * TILE_SIZE
    destinationRect.right = destinationRect.left + TILE_SIZE
    destinationRect.top = row * TILE_SIZE
    destinationRect.bottom = destinationRect.top + TILE_SIZE
  }

  fun decode(context: Context) {
    if (state != State.IDLE) {
      return
    }
    state = State.DECODING
    updateDestinationRect()
    val file = "tiles/phi-500000-${column}_$row.jpg"
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

  override fun equals(other: Any?): Boolean = other is Tile && other.column == column && other.row == row
  override fun hashCode() = Hashes.compute(17, 31, column, row)

}
