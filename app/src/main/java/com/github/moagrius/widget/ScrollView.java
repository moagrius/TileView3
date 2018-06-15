package com.github.moagrius.widget;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
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

  //private boolean mIsDragging;
  private boolean mSmoothScrollingEnabled = true;
  private long mLastScrolledAt;

  private VelocityTracker mVelocityTracker;

  private float mMinimumVelocity;
  private float mMaximumVelocity;

  private int mTouchSlop;
  private int mTouchSlopSquare;

  private int mInitialTouchX;
  private int mInitialTouchY;
  private int mLastTouchX;
  private int mLastTouchY;

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
    return mScrollState == SCROLL_STATE_DRAGGING;
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

  public static final int SCROLL_STATE_IDLE = 0;
  public static final int SCROLL_STATE_DRAGGING = 1;
  public static final int SCROLL_STATE_SETTLING = 2;

  private int mScrollState = SCROLL_STATE_IDLE;

  private ViewFlinger mViewFlinger = new ViewFlinger();

  private void setScrollState(int state) {
    if (state == mScrollState) {
      return;
    }
    mScrollState = state;
    if (state != SCROLL_STATE_SETTLING) {
      mViewFlinger.stop();
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent e) {
    if (mVelocityTracker == null) {
      mVelocityTracker = VelocityTracker.obtain();
    }
    mVelocityTracker.addMovement(e);
    final int action = e.getActionMasked();
    final int x = (int) e.getX();
    final int y = (int) e.getY();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        mInitialTouchX = mLastTouchX = x;
        mInitialTouchY = mLastTouchY = y;
        if (!mScroller.isFinished()) {
          mScroller.abortAnimation();
          mScroller.forceFinished(true);
        }
        if (mScrollState == SCROLL_STATE_SETTLING) {
          setScrollState(SCROLL_STATE_DRAGGING);
        }
        break;
      case MotionEvent.ACTION_MOVE:
        if (mScrollState != SCROLL_STATE_DRAGGING) {
          final int dx = x - mInitialTouchX;
          final int dy = y - mInitialTouchY;
          boolean startScroll = false;
          if (Math.abs(dx) > mTouchSlop) {
            mLastTouchX = mInitialTouchX + mTouchSlop * (dx < 0 ? -1 : 1);
            startScroll = true;
          }
          if (Math.abs(dy) > mTouchSlop) {
            mLastTouchY = mInitialTouchY + mTouchSlop * (dy < 0 ? -1 : 1);
            startScroll = true;
          }
          if (startScroll) {
            setScrollState(SCROLL_STATE_DRAGGING);
          }
        }
        break;
      case MotionEvent.ACTION_UP:
        mVelocityTracker.clear();
        break;
    }
    return mScrollState == SCROLL_STATE_DRAGGING;
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (mVelocityTracker == null) {
      mVelocityTracker = VelocityTracker.obtain();
    }
    mVelocityTracker.addMovement(e);
    final int action = e.getActionMasked();
    final int x = (int) e.getX();
    final int y = (int) e.getY();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        mInitialTouchX = mLastTouchX = x;
        mInitialTouchY = mLastTouchY = y;

        break;
      case MotionEvent.ACTION_MOVE:
        if (mScrollState != SCROLL_STATE_DRAGGING) {
          final int dx = x - mInitialTouchX;
          final int dy = y - mInitialTouchY;
          boolean startScroll = false;
          if (Math.abs(dx) > mTouchSlop) {
            mLastTouchX = mInitialTouchX + mTouchSlop * (dx < 0 ? -1 : 1);
            startScroll = true;
          }
          if (Math.abs(dy) > mTouchSlop) {
            mLastTouchY = mInitialTouchY + mTouchSlop * (dy < 0 ? -1 : 1);
            startScroll = true;
          }
          if (startScroll) {
            setScrollState(SCROLL_STATE_DRAGGING);
          }
        }
        if (mScrollState == SCROLL_STATE_DRAGGING) {
          final int dx = x - mLastTouchX;
          final int dy = y - mLastTouchX;
          scrollBy(dx, dy);
        }
        mLastTouchX = x;
        mLastTouchY = y;
        break;
      case MotionEvent.ACTION_UP:
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        final float velocityX = mVelocityTracker.getXVelocity();
        final float velocityY = mVelocityTracker.getYVelocity();
        if (velocityX != 0 || velocityY != 0) {
          fling((int) velocityX, (int) velocityY);
        } else {
          setScrollState(SCROLL_STATE_IDLE);
        }
        mVelocityTracker.clear();
        break;
    }
    return true;
  }

  public void fling(int velocityX, int velocityY) {
    if (Math.abs(velocityX) < mMinimumVelocity) {
      velocityX = 0;
    }
    if (Math.abs(velocityY) < mMinimumVelocity) {
      velocityY = 0;
    }
    velocityX = (int) Math.max(-mMaximumVelocity, Math.min(velocityX, mMaximumVelocity));
    velocityY = (int) Math.max(-mMaximumVelocity, Math.min(velocityY, mMaximumVelocity));
    if (velocityX != 0 || velocityY != 0) {
      mViewFlinger.fling(velocityX, velocityY);
      //mScroller.fling(getScrollX(), getScrollY(), -velocityX, -velocityY, getScrollMinX(), getHorizontalScrollRange(), getScrollMinY(), getVerticalScrollRange());
    }
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

  private class ViewFlinger implements Runnable {

    private static final int MAX_SCROLL_DURATION = 2000;

    private final Interpolator sQuinticInterpolator = new Interpolator() {
      public float getInterpolation(float t) {
        t -= 1.0f;
        return t * t * t * t * t + 1.0f;
      }
    };

    private int mLastFlingX;
    private int mLastFlingY;
    private Scroller mScroller;
    private Interpolator mInterpolator = sQuinticInterpolator;
    public ViewFlinger() {
      mScroller = new Scroller(getContext());
    }
    @Override
    public void run() {
      if (mScroller.computeScrollOffset()) {
        final int x = mScroller.getCurrX();
        final int y = mScroller.getCurrY();
        mLastFlingX = x;
        mLastFlingY = y;
        if (mScroller.isFinished()) {
          setScrollState(SCROLL_STATE_IDLE);
        } else {
          ViewCompat.postOnAnimation(ScrollView.this, this);
        }
      }
    }
    public void fling(int velocityX, int velocityY) {
      setScrollState(SCROLL_STATE_SETTLING);
      mLastFlingX = mLastFlingY = 0;
      mScroller.fling(0, 0, velocityX, velocityY,
          Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
      ViewCompat.postOnAnimation(ScrollView.this, this);
    }
    public void smoothScrollBy(int dx, int dy) {
      smoothScrollBy(dx, dy, 0, 0);
    }
    public void smoothScrollBy(int dx, int dy, int vx, int vy) {
      smoothScrollBy(dx, dy, computeScrollDuration(dx, dy, vx, vy));
    }
    private float distanceInfluenceForSnapDuration(float f) {
      f -= 0.5f; // center the values about 0.
      f *= 0.3f * Math.PI / 2.0f;
      return (float) Math.sin(f);
    }
    private int computeScrollDuration(int dx, int dy, int vx, int vy) {
      final int absDx = Math.abs(dx);
      final int absDy = Math.abs(dy);
      final boolean horizontal = absDx > absDy;
      final int velocity = (int) Math.sqrt(vx * vx + vy * vy);
      final int delta = (int) Math.sqrt(dx * dx + dy * dy);
      final int containerSize = horizontal ? getWidth() : getHeight();
      final int halfContainerSize = containerSize / 2;
      final float distanceRatio = Math.min(1.f, 1.f * delta / containerSize);
      final float distance = halfContainerSize + halfContainerSize *
          distanceInfluenceForSnapDuration(distanceRatio);
      final int duration;
      if (velocity > 0) {
        duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
      } else {
        duration = (int) ((((float) absDx / containerSize) + 1) * 300);
      }
      return Math.min(duration, MAX_SCROLL_DURATION);
    }
    public void smoothScrollBy(int dx, int dy, int duration) {
      smoothScrollBy(dx, dy, duration, sQuinticInterpolator);
    }
    public void smoothScrollBy(int dx, int dy, int duration, Interpolator interpolator) {
      if (mInterpolator != interpolator) {
        mInterpolator = interpolator;
        mScroller = new Scroller(getContext());
      }
      setScrollState(SCROLL_STATE_SETTLING);
      mLastFlingX = mLastFlingY = 0;
      mScroller.startScroll(0, 0, dx, dy, duration);
      ViewCompat.postOnAnimation(ScrollView.this, this);
    }
    public void stop() {
      removeCallbacks(this);
    }
  }

  public interface ScrollChangedListener {
    void onScrollChanged(ScrollView scrollView, int x, int y);
  }

}
