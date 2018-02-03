package com.github.moagrius.tileview3;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * @author Mike Dunn, 2/2/18.
 */

public class ZoomScrollView extends ScrollView implements
  GestureDetector.OnDoubleTapListener,
  ScaleGestureDetector.OnScaleGestureListener {

  public enum MinimumScaleMode {CONTAIN, COVER, NONE}

  private GestureDetector mGestureDetector;
  private ScaleGestureDetector mScaleGestureDetector;

  private ScaleChangedListener mScaleChangedListener;

  private ZoomScrollAnimator mZoomScrollAnimator;

  private MinimumScaleMode mMinimumScaleMode = MinimumScaleMode.COVER;

  private float mScale = 1;

  private float mMinScale = 0;
  private float mMaxScale = 1;

  private int mOffsetX;
  private int mOffsetY;

  private float mEffectiveMinScale = 0;
  private float mMinimumScaleX;
  private float mMinimumScaleY;
  private boolean mShouldLoopScale = true;

  public ZoomScrollView(Context context) {
    this(context, null);
  }

  public ZoomScrollView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ZoomScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    mScaleGestureDetector = new ScaleGestureDetector(context, this);
    mGestureDetector = new GestureDetector(context, this);
    mZoomScrollAnimator = new ZoomScrollAnimator(this);
  }

  @Override
  public boolean onTouchEvent( MotionEvent event ) {
    boolean gestureIntercept = mGestureDetector.onTouchEvent( event );
    boolean scaleIntercept = mScaleGestureDetector.onTouchEvent( event );
    return gestureIntercept || scaleIntercept || super.onTouchEvent( event );
  }

  // getters and setters

  public float getScale() {
    return mScale;
  }

  public void setScale(float scale) {
    scale = getConstrainedDestinationScale(scale);
    if (mScale != scale) {
      float previous = mScale;
      mScale = scale;
      resetScrollPositionToWithinLimits();
      invalidate();
      if (mScaleChangedListener != null) {
        mScaleChangedListener.onScaleChanged(this, mScale, previous);
      }
    }
  }

  public ZoomScrollAnimator getAnimator() {
    return mZoomScrollAnimator;
  }

  // scale limits

  private void calculateMinimumScaleToFit() {
    mMinimumScaleX = getWidth() / (float) getContentWidth();
    mMinimumScaleY = getHeight() / (float) getContentHeight();
    float recalculatedMinScale = computeMinimumScaleForMode(mMinimumScaleX, mMinimumScaleY);
    if (recalculatedMinScale != mEffectiveMinScale) {
      mEffectiveMinScale = recalculatedMinScale;
      if (mScale < mEffectiveMinScale) {
        setScale(mEffectiveMinScale);
      }
    }
  }

  private float computeMinimumScaleForMode(float minimumScaleX, float minimumScaleY) {
    switch (mMinimumScaleMode) {
      case COVER:
        return Math.max(minimumScaleX, minimumScaleY);
      case CONTAIN:
        return Math.min(minimumScaleX, minimumScaleY);
    }
    return mMinScale;
  }

  public void setScaleLimits(float min, float max) {
    mMinScale = min;
    mMaxScale = max;
    setScale(mScale);
  }

  public void setMinimumScaleMode(MinimumScaleMode minimumScaleMode) {
    mMinimumScaleMode = minimumScaleMode;
    calculateMinimumScaleToFit();
  }

  @Override
  public int getContentWidth() {
    return (int) (super.getContentWidth() * mScale);
  }

  @Override
  public int getContentHeight() {
    return (int) (super.getContentHeight() * mScale);
  }

  private void resetScrollPositionToWithinLimits() {
    scrollTo(getScrollX(), getScrollY());
  }

  public void setShouldLoopScale(boolean shouldLoopScale) {
    mShouldLoopScale = shouldLoopScale;
  }

  public ScaleChangedListener getScaleChangedListener() {
    return mScaleChangedListener;
  }

  public void setScaleChangedListener(ScaleChangedListener scaleChangedListener) {
    mScaleChangedListener = scaleChangedListener;
  }

  // doers

  private float getConstrainedDestinationScale(float scale) {
    scale = Math.max(scale, mEffectiveMinScale);
    scale = Math.min(scale, mMaxScale);
    return scale;
  }

  private int getOffsetScrollXFromScale(int offsetX, float destinationScale, float currentScale) {
    int scrollX = getScrollX() + offsetX;
    float deltaScale = destinationScale / currentScale;
    return (int) (scrollX * deltaScale) - offsetX;
  }

  private int getOffsetScrollYFromScale(int offsetY, float destinationScale, float currentScale) {
    int scrollY = getScrollY() + offsetY;
    float deltaScale = destinationScale / currentScale;
    return (int) (scrollY * deltaScale) - offsetY;
  }

  public void setScaleFromPosition(int offsetX, int offsetY, float scale) {
    scale = getConstrainedDestinationScale(scale);
    if (scale == mScale) {
      return;
    }
    int x = getOffsetScrollXFromScale(offsetX, scale, mScale);
    int y = getOffsetScrollYFromScale(offsetY, scale, mScale);

    setScale(scale);

    x = getConstrainedScrollX(x);
    y = getConstrainedScrollY(y);

    scrollTo(x, y);
  }

  public void smoothScaleFromFocalPoint(int focusX, int focusY, float scale) {
    scale = getConstrainedDestinationScale(scale);
    if (scale == mScale) {
      return;
    }
    int x = getOffsetScrollXFromScale(focusX, scale, mScale);
    int y = getOffsetScrollYFromScale(focusY, scale, mScale);
    getAnimator().animate(x, y, scale);
  }

  public void smoothScaleFromCenter(float scale) {
    smoothScaleFromFocalPoint(getHalfWidth(), getHalfHeight(), scale);
  }

  // interface methods

  @Override
  public boolean onSingleTapConfirmed(MotionEvent event) {
    return false;
  }

  @Override
  public boolean onDoubleTap(MotionEvent event) {
    float destination = (float) (Math.pow(2, Math.floor(Math.log(mScale * 2) / Math.log(2))));
    float effectiveDestination = mShouldLoopScale && mScale >= mMaxScale ? mMinScale : destination;
    destination = getConstrainedDestinationScale(effectiveDestination);
    smoothScaleFromFocalPoint((int) event.getX(), (int) event.getY(), destination);
    return true;
  }

  @Override
  public boolean onDoubleTapEvent(MotionEvent event) {
    return true;
  }

  @Override
  public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
    float currentScale = mScale * mScaleGestureDetector.getScaleFactor();
    setScaleFromPosition(
      (int) scaleGestureDetector.getFocusX(),
      (int) scaleGestureDetector.getFocusY(),
      currentScale);
    return true;
  }

  @Override
  public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
    return false;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

  }

  public interface ScaleChangedListener {
    void onScaleChanged(ZoomScrollView zoomScrollView, float currentScale, float previousScale);
  }
  
}
