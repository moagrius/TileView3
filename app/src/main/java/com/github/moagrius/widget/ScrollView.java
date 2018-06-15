package com.github.moagrius.widget;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.Scroller;

/**
 * @author Mike Dunn, 6/11/17.
 *
 * Combination of original work and AOSP ScrollView and HorizontalScrollView
 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/ScrollView.java
 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/HorizontalScrollView.java
 */

public class ScrollView extends FrameLayout {

  private static final String ADD_VIEW_ERROR_MESSAGE = "ScrollView can host only one direct child";
  private static final int ANIMATED_SCROLL_GAP = 250;
  private static final int DIRECTION_BACKWARD = -1;
  private static final int DIRECTION_FORWARD = 1;

  private boolean mIsDragging;
  private boolean mSmoothScrollingEnabled = true;
  private long mLastScrolledAt;

  private VelocityTracker mVelocityTracker;

  private float mMinimumVelocity;
  private float mMaximumVelocity;

  private int mTouchSlop;
  private int mTouchSlopSquare;

  private int mLastMotionX;
  private int mLastMotionY;

  private Scroller mScroller;
  private ScrollChangedListener mScrollChangedListener;

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
    mScroller = new Scroller(context);
    ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
    mMinimumVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
    mMaximumVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
    mTouchSlop = viewConfiguration.getScaledTouchSlop();
    mTouchSlopSquare = mTouchSlop * mTouchSlop;
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

  public ScrollChangedListener getScrollChangedListener() {
    return mScrollChangedListener;
  }

  public void setScrollChangedListener(ScrollChangedListener scrollChangedListener) {
    mScrollChangedListener = scrollChangedListener;
  }

  public void setScroller(Scroller scroller) {
    mScroller = scroller;
  }

  public boolean isSmoothScrollingEnabled() {
    return mSmoothScrollingEnabled;
  }

  public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
    mSmoothScrollingEnabled = smoothScrollingEnabled;
  }

  @Override
  protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
    final int horizontalPadding = getPaddingLeft() + getPaddingRight();
    final int verticalPadding = getPaddingTop() + getPaddingBottom();
    final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentWidthMeasureSpec) - horizontalPadding), MeasureSpec.UNSPECIFIED);
    final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentHeightMeasureSpec) - verticalPadding), MeasureSpec.UNSPECIFIED);
    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  @Override
  protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
    MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
    final int horizontalUsedTotal = getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed;
    final int verticalUsedTotal = getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + heightUsed;
    final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentWidthMeasureSpec) - horizontalUsedTotal), MeasureSpec.UNSPECIFIED);
    final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(parentHeightMeasureSpec) - verticalUsedTotal), MeasureSpec.UNSPECIFIED);
    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    scrollTo(getScrollX(), getScrollY());
  }

  /**
   * Returns whether the ScrollView is currently being flung.
   *
   * @return true if the ScrollView is currently flinging, false otherwise.
   */
  public boolean isFlinging() {
    return mScroller != null && !mScroller.isFinished();
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
      mScroller.startScroll(getScrollX(), getScrollY(), x, y);
      awakenScrollBars();
      invalidate();
    } else {
      if (!mScroller.isFinished()) {
        mScroller.abortAnimation();
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
   * Scrolls and centers the ScrollView to the left and top values provided.
   *
   * @param x Horizontal destination point.
   * @param y Vertical destination point.
   */
  public void scrollToAndCenter(int x, int y) {
    scrollTo(x - getHalfWidth(), y - getHalfHeight());
  }

  /**
   * Scrolls and centers the ScrollView to the left and top values provided using scrolling animation.
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
    return direction > 0 ? position < getHorizontalScrollRange() : direction < 0 && position > 0;
  }

  public boolean canScrollHorizontally() {
    return canScrollHorizontally(DIRECTION_BACKWARD) || canScrollHorizontally(DIRECTION_FORWARD);
  }

  @Override
  public boolean canScrollVertically(int direction) {
    int position = getScrollY();
    return direction > 0 ? position < getVerticalScrollRange() : direction < 0 && position > 0;
  }

  public boolean canScrollVertically() {
    return canScrollVertically(DIRECTION_BACKWARD) || canScrollVertically(DIRECTION_FORWARD);
  }

  public boolean canScroll() {
    return canScrollHorizontally() || canScrollVertically();
  }

  private void performScrollBy(int x, int y) {
    if (mSmoothScrollingEnabled) {
      smoothScrollBy(x, y);
    } else {
      scrollBy(x, y);
    }
  }

  private void initOrResetVelocityTracker() {
    if (mVelocityTracker == null) {
      mVelocityTracker = VelocityTracker.obtain();
    } else {
      mVelocityTracker.clear();
    }
  }
  private void initVelocityTrackerIfNotExists() {
    if (mVelocityTracker == null) {
      mVelocityTracker = VelocityTracker.obtain();
    }
  }

  private void recycleVelocityTracker() {
    if (mVelocityTracker != null) {
      mVelocityTracker.recycle();
      mVelocityTracker = null;
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    final int action = event.getAction();
    if (action == MotionEvent.ACTION_MOVE && mIsDragging) {
      return true;
    }
    if (super.onInterceptTouchEvent(event)) {
      return true;
    }
    switch (action & MotionEvent.ACTION_MASK) {
      case MotionEvent.ACTION_MOVE: {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final int xDiff = Math.abs(x - mLastMotionX);
        final int yDiff = Math.abs(y - mLastMotionY);
        final int distance = (xDiff * xDiff) + (yDiff * yDiff);
        if (distance > mTouchSlopSquare) {
          mIsDragging = true;
          mLastMotionX = x;
          mLastMotionY = y;
          initVelocityTrackerIfNotExists();
          mVelocityTracker.addMovement(event);
          final ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
        }
        break;
      }
      case MotionEvent.ACTION_DOWN: {
        mLastMotionX = (int) event.getX();
        mLastMotionY = (int) event.getY();
        initOrResetVelocityTracker();
        mVelocityTracker.addMovement(event);
        mScroller.computeScrollOffset();
        mIsDragging = !mScroller.isFinished();
        break;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        mIsDragging = false;
        recycleVelocityTracker();
        break;
    }
    return mIsDragging;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    initVelocityTrackerIfNotExists();
    final int actionMasked = event.getActionMasked();
    switch (actionMasked) {
      case MotionEvent.ACTION_DOWN: {
        mIsDragging = !mScroller.isFinished();
        if (mIsDragging) {
          final ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
        }
        if (!mScroller.isFinished()) {
          mScroller.abortAnimation();
        }
        mLastMotionX = (int) event.getX();
        mLastMotionY = (int) event.getY();
        break;
      }
      case MotionEvent.ACTION_MOVE:
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        int deltaX = mLastMotionX - x;
        int deltaY = mLastMotionY - y;
        final int distance = (deltaX * deltaX) + (deltaY * deltaY);
        if (!mIsDragging && distance > mTouchSlopSquare) {
          final ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
          mIsDragging = true;
          if (deltaY > 0) {
            deltaY -= mTouchSlop;
          } else {
            deltaY += mTouchSlop;
          }
          if (deltaX > 0) {
            deltaX -= mTouchSlop;
          } else {
            deltaX += mTouchSlop;
          }
        }
        if (mIsDragging) {
          mLastMotionX = x;
          mLastMotionY = y;
          scrollBy(deltaX, deltaY);
        }
        break;
      case MotionEvent.ACTION_UP:
        if (mIsDragging) {
          mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
          int velocityX = (int) mVelocityTracker.getXVelocity();
          int velocityY = (int) mVelocityTracker.getYVelocity();
          int velocity = Math.max(Math.abs(velocityX), Math.abs(velocityY));
          if (velocity > mMinimumVelocity) {
            Log.d("SV", "should fling");
            mScroller.fling(
                getScrollX(), getScrollY(),
                -velocityX, -velocityY,
                getScrollMinX(), getHorizontalScrollRange(),
                getScrollMinY(), getVerticalScrollRange());
            ViewCompat.postInvalidateOnAnimation(this);
          }
          mIsDragging = false;
          recycleVelocityTracker();
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        if (mIsDragging) {
          mIsDragging = false;
          recycleVelocityTracker();
        }
        break;
    }
    return true;
  }

  @Override
  public void scrollTo(int x, int y) {
    x = getConstrainedScrollX(x);
    y = getConstrainedScrollY(y);
    boolean changed = (x != getScrollX()) || (y != getScrollY());
    super.scrollTo(x, y);
    if (changed && mScrollChangedListener != null) {
      mScrollChangedListener.onScrollChanged(this, x, y);
    }
  }

  @Override
  public void scrollBy(int x, int y) {
    scrollTo(getScrollX() + x, getScrollY() + y);
  }

  @Override
  public void computeScroll() {
    if (mScroller.computeScrollOffset()) {
      scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
      if (!mScroller.isFinished()) {
        ViewCompat.postInvalidateOnAnimation(this);
      }
    }
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
    return Math.max(getScrollMinX(), Math.min(x, getHorizontalScrollRange()));
  }

  /**
   * See {@link #getConstrainedScrollX(int)}
   */
  protected int getConstrainedScrollY(int y) {
    return Math.max(getScrollMinY(), Math.min(y, getVerticalScrollRange()));
  }

  protected int getContentBottom() {
    if (hasContent()) {
      return getChild().getTop() + getContentHeight();
    }
    return 0;
  }

  protected int getContentRight() {
    if (hasContent()) {
      return getChild().getLeft() + getContentWidth();
    }
    return 0;
  }

  private int getVerticalScrollRange() {
    if (!hasContent()) {
      return 0;
    }
    return Math.max(0, getContentBottom() - (getHeight() - getPaddingBottom() - getPaddingTop()));
  }

  private int getHorizontalScrollRange() {
    if (!hasContent()) {
      return 0;
    }
    return Math.max(0, getContentRight() - (getWidth() - getPaddingLeft() - getPaddingRight()));
  }

  protected int getScrollMinX() {
    return 0;
  }

  protected int getScrollMinY() {
    return 0;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
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
          // if we can scroll right
          if (canScrollHorizontally(DIRECTION_FORWARD)) {
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

  public interface ScrollChangedListener {
    void onScrollChanged(ScrollView scrollView, int x, int y);
  }

}
