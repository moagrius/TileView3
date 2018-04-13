package com.github.moagrius.view

import android.view.MotionEvent

/**
 * @author Mike Dunn, 10/6/15.
 */
class TouchUpGestureDetector(private val onTouchUpListener: OnTouchUpListener) {

  fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
      return onTouchUpListener.onTouchUp(event)
    }
    return true
  }

  interface OnTouchUpListener {
    fun onTouchUp(event: MotionEvent): Boolean
  }
}

