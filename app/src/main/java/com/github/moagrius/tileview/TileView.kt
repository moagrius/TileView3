package com.github.moagrius.tileview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.LruCache
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

// TODO:
// use the tiles from the 100% image.
// when scaled down to a new "detail level", inSampleSize doubles ( >> 1), which halves the size of the image
// then apply the reverse scale to the image (as is done in current TileView)
class TileView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr),
    ZoomScrollView.ScaleChangedListener,
    ScrollView.ScrollChangedListener {

  private var scale = 1f
    set(scale) {
      field = scale
      sampleSize = 1
      var current = 1F
      val divisor = 2F
      while (true) {
        val next = current / divisor
        if (next < scale) {
          break
        }
        sampleSize = sampleSize shl 1
        current = next
      }
    }

  private var sampleSize = 1

  private var zoomScrollView: ZoomScrollView? = null

  private val viewport = Rect()
  private val newlyVisibleTiles = HashSet<Tile>()
  private val tilesVisibleInViewport = HashSet<Tile>()

  private val executor = Executors.newFixedThreadPool(3)
  private val renderThrottle = Throttler(10)

  private val memoryCache = object: LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 4).toInt()) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
      // The cache size will be measured in kilobytes rather than number of items.
      return bitmap.byteCount / 1024
    }
  }

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
    Log.d("TV", "scale=$scale")
    updateViewportAndComputeTilesThrottled()
    invalidate()
  }

  override fun onScrollChanged(scrollView: ScrollView, x: Int, y: Int) {
    Log.d("T", "onScrollChanged")
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

  // TODO: when "clicking" over to or from a new sample size, we need to redraw all tiles
  private fun computeTilesInCurrentViewport() {
    Log.d("T", "computeTilesInCurrentViewport")
    Log.d("T", "current tile count: " + tilesVisibleInViewport.size)
    newlyVisibleTiles.clear()
    val tileSize = Tile.TILE_SIZE * scale
    val rowStart = floor(viewport.top / tileSize).toInt()
    val rowEnd = ceil(viewport.bottom / tileSize).toInt()
    val columnStart = floor(viewport.left / tileSize).toInt()
    val columnEnd = ceil(viewport.right / tileSize).toInt()
    Log.d("T", "$rowStart, $rowEnd, $columnStart, $columnEnd")
    for (rowCurrent in rowStart..rowEnd) {
      for (columnCurrent in columnStart..columnEnd) {
        val tile = Tile()
        tile.column = columnCurrent
        tile.row = rowCurrent
        tile.sample = sampleSize
        newlyVisibleTiles.add(tile)
      }
    }
    Log.d("T", "newly visible: " + newlyVisibleTiles.size)
    val previousAndCurrentlyVisibleTileIterator = tilesVisibleInViewport.iterator()
    while (previousAndCurrentlyVisibleTileIterator.hasNext()) {
      val tile = previousAndCurrentlyVisibleTileIterator.next()
      if (!newlyVisibleTiles.contains(tile)) {
        previousAndCurrentlyVisibleTileIterator.remove()
      }
    }
    for (tile in newlyVisibleTiles) {
      val added = tilesVisibleInViewport.add(tile)
      if (added) {
        executor.execute {
          tile.decode(context, memoryCache)
          postInvalidate()
        }
      }
    }
    Log.d("T", "current tile count: ${tilesVisibleInViewport.size}")
  }

}
