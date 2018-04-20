package com.github.moagrius.tileview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.github.moagrius.utils.Throttler
import com.github.moagrius.widget.ScrollView
import com.github.moagrius.widget.ZoomScrollView
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor

/**
 * @author Mike Dunn, 2/3/18.
 */

class TileView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr),
    ZoomScrollView.ScaleChangedListener,
    ScrollView.ScrollChangedListener {

  private val bitmapOptions = BitmapFactory.Options()

  init {
    //https://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inTempStorage
    bitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565
    bitmapOptions.inTempStorage = ByteArray(16 * 1024)
    bitmapOptions.inSampleSize = 1
  }

  private var scale = 1f
    set(scale) {
      field = scale
      val previous = bitmapOptions.inSampleSize
      bitmapOptions.inSampleSize = 1
      var current = 1F
      val divisor = 2F
      while (true) {
        val next = current / divisor
        if (next < scale) {
          break
        }
        bitmapOptions.inSampleSize = bitmapOptions.inSampleSize shl 1
        current = next
      }
      if (bitmapOptions.inSampleSize != previous) {
        tilesVisibleInViewport.clear()
      }
      Log.d("DL", "sample: ${bitmapOptions.inSampleSize}")
    }

  private var zoomScrollView: ZoomScrollView? = null

  private val viewport = Rect()
  private val newlyVisibleTiles = HashSet<Tile>()
  private val tilesVisibleInViewport = HashSet<Tile>()

  private val executor = Executors.newFixedThreadPool(3)
  private val renderThrottle = Throttler(10)

  private val memoryCache = MemoryCache(((Runtime.getRuntime().maxMemory() / 1024) / 4).toInt())

  private val updateAndComputeTilesRunnable = Runnable {
    updateViewport()
    computeTilesInCurrentViewport()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (parent !is ZoomScrollView) {
      throw IllegalStateException("TileView must be a child of a ZoomScrollView")
    }
    zoomScrollView = parent as ZoomScrollView
    zoomScrollView?.let {
      it.scrollChangedListener = this
      it.scaleChangedListener = this
      updateViewportAndComputeTilesThrottled()
    }
  }

  override fun onScaleChanged(zoomScrollView: ZoomScrollView, currentScale: Float, previousScale: Float) {
    scale = currentScale
    updateViewportAndComputeTilesThrottled()
    invalidate()
  }

  override fun onScrollChanged(scrollView: ScrollView, x: Int, y: Int) {
    updateViewportAndComputeTilesThrottled()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    updateViewportAndComputeTilesThrottled()
  }

  public override fun onDraw(canvas: Canvas) {
    canvas.scale(scale, scale)
    for (tile in tilesVisibleInViewport) {
      tile.draw(canvas)
    }
  }

  private fun updateViewportAndComputeTilesThrottled() {
    renderThrottle.attempt(updateAndComputeTilesRunnable)
  }

  private fun updateViewport() {
    zoomScrollView?.let {
      viewport.set(
          it.scrollX,
          it.scrollY,
          it.measuredWidth + it.scrollX,
          it.measuredHeight + it.scrollY)
    }
  }

  private fun computeTilesInCurrentViewport() {
    newlyVisibleTiles.clear()
    val tileSize = Tile.TILE_SIZE * scale
    val rowStart = floor(viewport.top / tileSize).toInt()
    val rowEnd = ceil(viewport.bottom / tileSize).toInt()
    val columnStart = floor(viewport.left / tileSize).toInt()
    val columnEnd = ceil(viewport.right / tileSize).toInt()
    //Log.d("T", "$rowStart, $rowEnd, $columnStart, $columnEnd")
    val sample = bitmapOptions.inSampleSize
    for (row in rowStart..rowEnd) {
      if (row % sample != 0) {
        continue
      }
      for (column in columnStart..columnEnd) {
        if (column % sample != 0) {
          continue
        }
        val tile = Tile()
        tile.options = bitmapOptions
        tile.startColumn = column
        tile.startRow = row
        newlyVisibleTiles.add(tile)
      }
    }
    val previousAndCurrentlyVisibleTileIterator = tilesVisibleInViewport.iterator()
    while (previousAndCurrentlyVisibleTileIterator.hasNext()) {
      val tile = previousAndCurrentlyVisibleTileIterator.next()
      if (!newlyVisibleTiles.contains(tile)) {
        previousAndCurrentlyVisibleTileIterator.remove()
      }
    }
    for (tile in newlyVisibleTiles) {
      val added = tilesVisibleInViewport.add(tile)
      // TODO: anything that's decoding that isn't in the set should be stopped
      if (added) {
        executor.execute {
          try {
            tile.decode(context, memoryCache)
            postInvalidate()
          } catch (e: Exception) {
            Log.d("TV", "exception decoding: ${e.message}")
          }
        }
      }
    }
  }

}
