package com.github.moagrius.tileview3;

import android.animation.ValueAnimator;
import android.view.animation.Interpolator;

/**
 * @author Mike Dunn, 2/2/18.
 */

public class ZoomAnimator extends ValueAnimator implements
  ValueAnimator.AnimatorUpdateListener {

  private ZoomScrollView mZoomScrollView;

  public ZoomAnimator(ZoomScrollView zoomScrollView) {
    super();
    mZoomScrollView = zoomScrollView;
    addUpdateListener( this );
    setInterpolator( new FastEaseInInterpolator() );
  }

  public void animate(float scale ) {
    float start = mZoomScrollView.getScale();
    if (start == scale) {
      return;
    }
    setFloatValues(start, scale);
    start();
  }

  @Override
  public void onAnimationUpdate( ValueAnimator animation ) {
    mZoomScrollView.setScale((float) animation.getAnimatedValue());
    //float scale = mStart + (mDestination - mStart) * (float) animation.getAnimatedValue();
    //mZoomScrollView.setScale( scale );
  }

  private static class FastEaseInInterpolator implements Interpolator {
    @Override
    public float getInterpolation( float input ) {
      return (float) (1 - Math.pow( 1 - input, 8 ));
    }
  }
}
