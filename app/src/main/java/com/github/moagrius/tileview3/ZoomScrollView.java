package com.github.moagrius.tileview3;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
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

  private float mScale = 1f;
  private float mMinScale = 0f;
  private float mMaxScale = 1f;
  private float mEffectiveMinScale = 0f;

  private boolean mWillHandleContentSize;
  private boolean mShouldVisuallyScaleContents;
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
  public boolean onTouchEvent(MotionEvent event) {
    boolean gestureIntercept = mGestureDetector.onTouchEvent(event);
    boolean scaleIntercept = mScaleGestureDetector.onTouchEvent(event);
    return gestureIntercept || scaleIntercept || super.onTouchEvent(event);
  }

  @Override
  protected void onLayout( boolean changed, int l, int t, int r, int b ) {
    super.onLayout(changed, l, t, r, b);
    calculateMinimumScaleToFit();
  }

  // getters and setters

  public ScaleChangedListener getScaleChangedListener() {
    return mScaleChangedListener;
  }

  public void setScaleChangedListener(ScaleChangedListener scaleChangedListener) {
    mScaleChangedListener = scaleChangedListener;
  }

  public float getScale() {
    return mScale;
  }

  public void setScale(float scale) {
    scale = getConstrainedDestinationScale(scale);
    if (mScale != scale) {
      float previous = mScale;
      mScale = scale;
      resetScrollPositionToWithinLimits();
      if (mShouldVisuallyScaleContents && hasContent()) {
        getChild().setPivotX(0);
        getChild().setPivotY(0);  // TODO: this is a hassle to prefab but would be more efficient
        getChild().setScaleX(mScale);
        getChild().setScaleY(mScale);
      }
      if (mScaleChangedListener != null) {
        mScaleChangedListener.onScaleChanged(this, mScale, previous);
      }
      invalidate();
    }
  }

  public ZoomScrollAnimator getAnimator() {
    return mZoomScrollAnimator;
  }

  // scale limits

  private void calculateMinimumScaleToFit() {
    float minimumScaleX = getWidth() / (float) getContentWidth();
    Log.d("Z", "min scale, width=" + getWidth() + ", content=" + getContentWidth() + ", minscalex=" + minimumScaleX);
    float minimumScaleY = getHeight() / (float) getContentHeight();
    float recalculatedMinScale = computeMinimumScaleForMode(minimumScaleX, minimumScaleY);
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
    if (mWillHandleContentSize) {
      return super.getContentWidth();
    }
    return (int) (super.getContentWidth() * mScale);
  }

  @Override
  public int getContentHeight() {
    if (mWillHandleContentSize) {
      return super.getContentHeight();
    }
    return (int) (super.getContentHeight() * mScale);
  }

  private void resetScrollPositionToWithinLimits() {
    scrollTo(getScrollX(), getScrollY());
  }

  public void setShouldLoopScale(boolean shouldLoopScale) {
    mShouldLoopScale = shouldLoopScale;
  }

  // normally we constrain scroll to scaled "size", which is not appropriate if the child is resizing itself based on scale
  public void setWillHandleContentSize(boolean willHandleContentSize) {
    mWillHandleContentSize = willHandleContentSize;
  }

  public void setShouldVisuallyScaleContents(boolean shouldVisuallyScaleContents) {
    mShouldVisuallyScaleContents = shouldVisuallyScaleContents;
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
    Log.d("Z", "pinching...");
    float currentScale = mScale * mScaleGestureDetector.getScaleFactor();
    setScaleFromPosition(
      (int) scaleGestureDetector.getFocusX(),
      (int) scaleGestureDetector.getFocusY(),
      currentScale);
    return true;
  }

  @Override
  public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
    return true;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

  }

  public interface ScaleChangedListener {
    void onScaleChanged(ZoomScrollView zoomScrollView, float currentScale, float previousScale);
  }

}
