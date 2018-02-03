package com.github.moagrius.tileview3;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.OverScroller;

/**
 * TODO: overscroll methods
 *
 * @author Mike Dunn, 6/11/17.
 */

public class ScrollView extends FrameLayout implements
  GestureDetector.OnGestureListener,
  TouchUpGestureDetector.OnTouchUpListener {

  private static final String ADD_VIEW_ERROR_MESSAGE = "ScrollView can host only one direct child";

  private static final int ANIMATED_SCROLL_GAP = 250;

  private static final int DIRECTION_BACKWARD = -1;
  private static final int DIRECTION_FORWARD = 1;

  private boolean mIsFlinging;
  private boolean mIsDragging;

  private boolean mSmoothScrollingEnabled = true;

  private long mLastScrolledAt;

  private OverScroller mOverScroller;

  private GestureDetector mGestureDetector;
  private TouchUpGestureDetector mTouchUpGestureDetector;

  private ZoomAnimator mAnimator;

  /**
   * Constructor to use when creating a ScrollView from code.
   *
   * @param context The Context the ScrollView is running in, through which it can access the current theme, resources, etc.
   */
  public ScrollView(Context context) {
    this(context, null);
  }

  public ScrollView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setWillNotDraw(false);
    setClipChildren(false);
    setFocusable(true);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    mGestureDetector = new GestureDetector(context, this);
    mTouchUpGestureDetector = new TouchUpGestureDetector(this);
    mOverScroller = new OverScroller(context);
  }

  private void assertSingleChild() {
    if (getChildCount() > 0) {
      throw new IllegalStateException(ADD_VIEW_ERROR_MESSAGE);
    }
  }

  @Override
  public void addView(View child) {
    assertSingleChild();
    super.addView(child);
  }

  @Override
  public void addView(View child, int index) {
    assertSingleChild();
    super.addView(child, index);
  }

  @Override
  public void addView(View child, ViewGroup.LayoutParams params) {
    assertSingleChild();
    super.addView(child, params);
  }

  @Override
  public void addView(View child, int index, ViewGroup.LayoutParams params) {
    assertSingleChild();
    super.addView(child, index, params);
  }

  public void setOverScroller(OverScroller overScroller) {
    mOverScroller = overScroller;
  }

  public boolean isSmoothScrollingEnabled() {
    return mSmoothScrollingEnabled;
  }

  public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
    mSmoothScrollingEnabled = smoothScrollingEnabled;
  }

  /**
   * Ask one of the children of this view to measure itself, taking into
   * account both the MeasureSpec requirements for this view and its padding.
   * The heavy lifting is done in getChildMeasureSpec.
   *
   * @param child                   The child to measure
   * @param parentWidthMeasureSpec  The width requirements for this view
   * @param parentHeightMeasureSpec The height requirements for this view
   */
  protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
    LayoutParams lp = (LayoutParams) child.getLayoutParams();
    int childWidthMeasureSpec = getScrollViewChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight(), lp.width);
    int childHeightMeasureSpec = getScrollViewChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop() + getPaddingBottom(), lp.height);
    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  /**
   * Ask one of the children of this view to measure itself, taking into
   * account both the MeasureSpec requirements for this view and its padding
   * and margins. The child must have MarginLayoutParams The heavy lifting is
   * done in getChildMeasureSpec.
   *
   * @param child                   The child to measure
   * @param parentWidthMeasureSpec  The width requirements for this view
   * @param widthUsed               Extra space that has been used up by the parent
   *                                horizontally (possibly by other children of the parent)
   * @param parentHeightMeasureSpec The height requirements for this view
   * @param heightUsed              Extra space that has been used up by the parent
   *                                vertically (possibly by other children of the parent)
   */
  protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
    MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
    int childWidthMeasureSpec = getScrollViewChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
    int childHeightMeasureSpec = getScrollViewChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  /**
   * Does the hard part of measureChildren: figuring out the MeasureSpec to
   * pass to a particular child. This method figures out the right MeasureSpec
   * for one dimension (height or width) of one child view.
   *
   * The goal is to combine information from our MeasureSpec with the
   * LayoutParams of the child to get the best possible results. For example,
   * if the this view knows its size (because its MeasureSpec has a mode of
   * EXACTLY), and the child has indicated in its LayoutParams that it wants
   * to be the same size as the parent, the parent should ask the child to
   * layout given an exact size.
   *
   * @param spec           The requirements for this view
   * @param padding        The padding of this view for the current dimension and
   *                       margins, if applicable
   * @param childDimension How big the child wants to be in the current
   *                       dimension
   * @return a MeasureSpec integer for the child
   */
  public static int getScrollViewChildMeasureSpec(int spec, int padding, int childDimension) {
    int specMode = MeasureSpec.getMode(spec);
    int specSize = MeasureSpec.getSize(spec);

    int size = Math.max(0, specSize - padding);

    int resultSize = 0;
    int resultMode = 0;

    switch (specMode) {
      case MeasureSpec.EXACTLY:
        if (childDimension >= 0) {
          resultSize = childDimension;
          resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.MATCH_PARENT) {
          resultSize = size;
          resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.WRAP_CONTENT) {
          resultSize = size;
          resultMode = MeasureSpec.UNSPECIFIED;
        }
        break;

      case MeasureSpec.AT_MOST:
        if (childDimension >= 0) {
          resultSize = childDimension;
          resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.MATCH_PARENT) {
          resultSize = size;
          resultMode = MeasureSpec.AT_MOST;
        } else if (childDimension == LayoutParams.WRAP_CONTENT) {
          resultSize = size;
          resultMode = MeasureSpec.UNSPECIFIED;
        }
        break;

      case MeasureSpec.UNSPECIFIED:
        if (childDimension >= 0) {
          resultSize = childDimension;
          resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.MATCH_PARENT) {
          resultSize = size;
          resultMode = MeasureSpec.UNSPECIFIED;
        } else if (childDimension == LayoutParams.WRAP_CONTENT) {
          resultSize = size;
          resultMode = MeasureSpec.UNSPECIFIED;
        }
        break;
    }
    //noinspection ResourceType
    return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    Log.d("ScrollView", "layout height=" + (b - t) + ", " + getChild().getMeasuredHeight());
    scrollTo(getScrollX(), getScrollY());
  }

  /**
   * Returns whether the ScrollView is currently being flung.
   *
   * @return true if the ScrollView is currently flinging, false otherwise.
   */
  public boolean isFlinging() {
    return mIsFlinging;
  }

  /**
   * Returns whether the ScrollView is currently being dragged.
   *
   * @return true if the ScrollView is currently dragging, false otherwise.
   */
  public boolean isDragging() {
    return mIsDragging;
  }

  /**
   * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
   *
   * @param x the number of pixels to scroll by on the X axis
   * @param y the number of pixels to scroll by on the Y axis
   */
  public void smoothScrollBy(int x, int y) {
    long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScrolledAt;
    if (duration > ANIMATED_SCROLL_GAP) {
      mOverScroller.startScroll(getScrollX(), getScrollY(), x, y);
      awakenScrollBars();
      invalidate();
    } else {
      if (!mOverScroller.isFinished()) {
        mOverScroller.abortAnimation();
      }
      scrollBy(x, y);
    }
    mLastScrolledAt = AnimationUtils.currentAnimationTimeMillis();
  }

  /**
   * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
   *
   * @param x the position where to scroll on the X axis
   * @param y the position where to scroll on the Y axis
   */
  public void smoothScrollTo(int x, int y) {
    smoothScrollBy(x - getScrollX(), y - getScrollY());
  }

  /**
   * Scrolls and centers the ScrollView to the x and y values provided.
   *
   * @param x Horizontal destination point.
   * @param y Vertical destination point.
   */
  public void scrollToAndCenter(int x, int y) {
    scrollTo(x - getHalfWidth(), y - getHalfHeight());
  }

  /**
   * Scrolls and centers the ScrollView to the x and y values provided using scrolling animation.
   *
   * @param x Horizontal destination point.
   * @param y Vertical destination point.
   */
  public void smoothScrollToAndCenter(int x, int y) {
    smoothScrollTo(x - getHalfWidth(), y - getHalfHeight());
  }

  @Override
  public boolean canScrollHorizontally(int direction) {
    int position = getScrollX();
    return direction > 0 ? position < getScrollLimitX() : direction < 0 && position > 0;
  }

  @Override
  public boolean canScrollVertically(int direction) {
    int position = getScrollY();
    return direction > 0 ? position < getScrollLimitY() : direction < 0 && position > 0;
  }

  public boolean canScroll(int direction) {
    return canScrollVertically(direction) || canScrollHorizontally(direction);
  }

  public boolean canScroll() {
    return canScroll(DIRECTION_FORWARD) || canScroll(DIRECTION_BACKWARD);
  }

  private void performScrollBy(int x, int y) {
    if (mSmoothScrollingEnabled) {
      smoothScrollBy(x, y);
    } else {
      scrollBy(x, y);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    boolean gestureIntercept = mGestureDetector.onTouchEvent(event);
    boolean touchIntercept = mTouchUpGestureDetector.onTouchEvent(event);
    return gestureIntercept || touchIntercept || super.onTouchEvent(event);
  }

  @Override
  public void scrollTo(int x, int y) {
    x = getConstrainedScrollX(x);
    y = getConstrainedScrollY(y);
    super.scrollTo(x, y);
  }

  protected boolean hasContent() {
    return getChildCount() > 0;
  }

  protected View getChild() {
    if (hasContent()) {
      return getChildAt(0);
    }
    return null;
  }

  public int getContentWidth() {
    if (hasContent()) {
      return getChild().getMeasuredWidth();
    }
    return 0;
  }

  public int getContentHeight() {
    if (hasContent()) {
      return getChild().getMeasuredHeight();
    }
    return 0;
  }

  protected int getHalfWidth() {
    return (int) ((getWidth() * 0.5f) + 0.5);
  }

  protected int getHalfHeight() {
    return (int) ((getHeight() * 0.5f) + 0.5);
  }

  /**
   * This strategy is used to avoid that a custom return value of {@link #getScrollMinX} (which
   * default to 0) become the return value of this method which shifts the whole TileView.
   */
  protected int getConstrainedScrollX(int x) {
    return Math.max(getScrollMinX(), Math.min(x, getScrollLimitX()));
  }

  /**
   * See {@link #getConstrainedScrollX(int)}
   */
  protected int getConstrainedScrollY(int y) {
    return Math.max(getScrollMinY(), Math.min(y, getScrollLimitY()));
  }

  protected int getContentRight() {
    if (hasContent()) {
      return getChild().getLeft() + getContentWidth();
    }
    return 0;
  }

  protected int getScrollLimitX() {
    if (hasContent()) {
      return getContentRight() - getWidth(); // Math.max(0, blah) ?
    }
    return 0;
  }

  protected int getContentBottom() {
    if (hasContent()) {
      return getChild().getTop() + getContentHeight();
    }
    return 0;
  }

  protected int getScrollLimitY() {
    if (hasContent()) {
      return getContentBottom() - getHeight();  // Math.max(0, blah) ?
    }
    return 0;
  }

  protected int getScrollMinX() {
    return 0;
  }

  protected int getScrollMinY() {
    return 0;
  }

  @Override
  public void computeScroll() {
    if (mOverScroller.computeScrollOffset()) {
      scrollTo(mOverScroller.getCurrX(), mOverScroller.getCurrY());
      mIsFlinging = !mOverScroller.isFinished();
      if (mIsFlinging) {
        ViewCompat.postInvalidateOnAnimation(this);
      }
    }
  }

  @Override
  public boolean onDown(MotionEvent event) {
    if (mIsFlinging && !mOverScroller.isFinished()) {
      mOverScroller.abortAnimation();
      mOverScroller.forceFinished(true);
    }
    return true;
  }

  @Override
  public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
    mOverScroller.fling(
      getScrollX(), getScrollY(),
      (int) -velocityX, (int) -velocityY,
      getScrollMinX(), getScrollLimitX(),
      getScrollMinY(), getScrollLimitY());
    ViewCompat.postInvalidateOnAnimation(this);
    return true;
  }

  @Override
  public void onLongPress(MotionEvent event) {

  }

  @Override
  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    Log.d("ScrollView", "onScroll");
    if (!mIsDragging) {
      mIsDragging = true;
    }
    int scrollEndX = getScrollX() + (int) distanceX;
    int scrollEndY = getScrollY() + (int) distanceY;
    scrollTo(scrollEndX, scrollEndY);
    return true;
  }

  @Override
  public void onShowPress(MotionEvent event) {

  }

  @Override
  public boolean onSingleTapUp(MotionEvent event) {
    return true;
  }

  @Override
  public boolean onTouchUp(MotionEvent event) {
    if (mIsDragging) {
      mIsDragging = false;
    }
    return true;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    Log.d("ScrollView", "dispatchKeyEvent");
    return super.dispatchKeyEvent(event) || executeKeyEvent(event);
  }

  /**
   * You can call this function yourself to have the scroll view perform
   * scrolling from a key event, just as if the event had been dispatched to
   * it by the view hierarchy.
   *
   * @param event The key event to execute.
   * @return Return true if the event was handled, else false.
   */
  public boolean executeKeyEvent(KeyEvent event) {
    Log.d("ScrollView", "executeKeyEvent: " + event.getKeyCode() + ", " + KeyEvent.KEYCODE_DPAD_RIGHT);
    // this reads a bit goofy to me (any key advances focus), but it's _exactly_ what android.widget.ScrollView does, so w/e...
    if (!canScroll()) {
      if (isFocused()) {
        View currentFocused = findFocus();
        if (currentFocused == this) {
          currentFocused = null;
        }
        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, View.FOCUS_DOWN);
        return nextFocused != null && nextFocused != this && nextFocused.requestFocus(View.FOCUS_DOWN);
      }
      return false;
    }
    boolean alt = event.isAltPressed();
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_UP:
          // if we can scroll up
          if (canScrollVertically(DIRECTION_BACKWARD)) {
            // if alt is down, scroll all the way home
            if (alt) {
              performScrollBy(0, -getScrollY());
            } else {  // otherwise scroll up one "page" (height)
              performScrollBy(0, -getHeight());
            }
            return true;
          }
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          // if we can scroll down
          if (canScrollVertically(DIRECTION_FORWARD)) {
            // if alt is down, scroll all the way to the end of content
            if (alt) {
              performScrollBy(0, getChild().getMeasuredHeight() - getScrollY());
            } else {  // otherwise scroll down one "page" (height)
              performScrollBy(0, getHeight());
            }
            return true;
          }
          break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
          // if we can scroll left
          if (canScrollHorizontally(DIRECTION_BACKWARD)) {
            // if alt is down, scroll all the way home
            if (alt) {
              performScrollBy(0, -getScrollX());
            } else {  // otherwise scroll left one "page" (width)
              performScrollBy(0, -getWidth());
            }
            return true;
          }
          break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          Log.d("ScrollView", "key right");
          // if we can scroll right
          if (canScrollHorizontally(DIRECTION_FORWARD)) {
            Log.d("ScrollView", "can scroll right");
            // if alt is down, scroll all the way to the end of content
            if (alt) {
              performScrollBy(getChild().getMeasuredWidth() - getScrollX(), 0);
            } else {  // otherwise scroll right one "page" (width)
              performScrollBy(getWidth(), 0);
            }
            return true;
          }
          break;
      }
    }
    return false;
  }
}
