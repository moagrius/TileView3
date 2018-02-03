package com.github.moagrius.tileview3;

import android.animation.ValueAnimator;
import android.view.animation.Interpolator;

import java.lang.ref.WeakReference;

/**
 * @author Mike Dunn, 2/2/18.
 */

public class ZoomScrollAnimator extends ValueAnimator implements ValueAnimator.AnimatorUpdateListener {

  private WeakReference<ZoomScrollView> mZoomScrollViewWeakReference;
  private ZoomScrollState mStartState = new ZoomScrollState();
  private ZoomScrollState mEndState = new ZoomScrollState();
  private boolean mHasPendingZoomUpdates;
  private boolean mHasPendingScrollUpdates;

  public ZoomScrollAnimator(ZoomScrollView zoomScrollView) {
    super();
    addUpdateListener(this);
    setFloatValues(0f, 1f);
    setInterpolator(new FastEaseInInterpolator());
    mZoomScrollViewWeakReference = new WeakReference<>(zoomScrollView);
  }

  private boolean setupScrollAnimation(int x, int y) {
    ZoomScrollView zoomScrollView = mZoomScrollViewWeakReference.get();
    if (zoomScrollView != null) {
      mStartState.x = zoomScrollView.getScrollX();
      mStartState.y = zoomScrollView.getScrollY();
      mEndState.x = x;
      mEndState.y = y;
      return mStartState.x != mEndState.x || mStartState.y != mEndState.y;
    }
    return false;
  }

  private boolean setupZoomAnimation(float scale) {
    ZoomScrollView zoomScrollView = mZoomScrollViewWeakReference.get();
    if (zoomScrollView != null) {
      mStartState.scale = zoomScrollView.getScale();
      mEndState.scale = scale;
      return mStartState.scale != mEndState.scale;
    }
    return false;
  }

  public void animate(int x, int y, float scale) {
    ZoomScrollView zoomScrollView = mZoomScrollViewWeakReference.get();
    if (zoomScrollView != null) {
      mHasPendingZoomUpdates = setupZoomAnimation(scale);
      mHasPendingScrollUpdates = setupScrollAnimation(x, y);
      if (mHasPendingScrollUpdates || mHasPendingZoomUpdates) {
        start();
      }
    }
  }

  public void animateZoom(float scale) {
    ZoomScrollView zoomScrollView = mZoomScrollViewWeakReference.get();
    if (zoomScrollView != null) {
      mHasPendingZoomUpdates = setupZoomAnimation(scale);
      if (mHasPendingZoomUpdates) {
        start();
      }
    }
  }

  public void animateScroll(int x, int y) {
    ZoomScrollView zoomScrollView = mZoomScrollViewWeakReference.get();
    if (zoomScrollView != null) {
      mHasPendingScrollUpdates = setupScrollAnimation(x, y);
      if (mHasPendingScrollUpdates) {
        start();
      }
    }
  }

  @Override
  public void onAnimationUpdate(ValueAnimator animation) {
    ZoomScrollView zoomScrollView = mZoomScrollViewWeakReference.get();
    if (zoomScrollView != null) {
      float progress = (float) animation.getAnimatedValue();
      if (mHasPendingZoomUpdates) {
        float scale = mStartState.scale + (mEndState.scale - mStartState.scale) * progress;
        zoomScrollView.setScale(scale);
      }
      if (mHasPendingScrollUpdates) {
        int x = (int) (mStartState.x + (mEndState.x - mStartState.x) * progress);
        int y = (int) (mStartState.y + (mEndState.y - mStartState.y) * progress);
        zoomScrollView.scrollTo(x, y);
      }
    }
  }

  private static class ZoomScrollState {
    public int x;
    public int y;
    public float scale;
  }

  private static class FastEaseInInterpolator implements Interpolator {
    @Override
    public float getInterpolation(float input) {
      return (float) (1 - Math.pow(1 - input, 8));
    }
  }
}
