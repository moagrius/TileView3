package com.github.moagrius.framework;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import java.util.List;

/**
 * This is a 2D scroller modified from ScrollView and HorizontalScrollView,
 * taken from the KitKat release (API 16)
 * https://android.googlesource.com/platform/frameworks/base/+/kitkat-release/core/java/android/widget/ScrollView.java
 * https://android.googlesource.com/platform/frameworks/base/+/kitkat-release/core/java/android/widget/HorizontalScrollView.java
 * with references made to the most modern version at the time of this writing:
 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/ScrollView.java
 *
 * I've modified only what was either unavailable (package-private, internal, etc) or what was required to function on both axes.
 *
 * Mike Dunn 2018
 */

public class ScrollView extends FrameLayout {

  static final int ANIMATED_SCROLL_GAP = 250;
  static final float MAX_SCROLL_FACTOR = 0.5f;

  private static final String TAG = "ScrollView";
  private static final int INVALID_POINTER = -1;

  private static final int DIRECTION_BACKWARD = -1;
  private static final int DIRECTION_FORWARD = 1;

  private static final String ADD_VIEW_ERROR_MESSAGE = "ScrollView can host only one direct child";

  private long mLastScroll;
  private final Rect mTempRect = new Rect();
  private OverScroller mScroller;
  private EdgeEffect mEdgeGlowTop;
  private EdgeEffect mEdgeGlowBottom;
  private int mLastMotionY;
  private boolean mIsLayoutDirty = true;
  private View mChildToScrollTo = null;
  private boolean mIsBeingDragged = false;
  private VelocityTracker mVelocityTracker;
  private boolean mFillViewport;
  private boolean mSmoothScrollingEnabled = true;
  private int mTouchSlop;
  private int mMinimumVelocity;
  private int mMaximumVelocity;
  private int mOverscrollDistance;
  private int mOverflingDistance;
  private int mActivePointerId = INVALID_POINTER;
  private SavedState mSavedState;

  public ScrollView(Context context) {
    this(context, null);
  }

  public ScrollView(Context context, AttributeSet attrs) {
    this(context, attrs, com.android.internal.R.attr.scrollViewStyle);
  }

  public ScrollView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initScrollView();
    TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.ScrollView, defStyle, 0);
    setFillViewport(a.getBoolean(com.android.internal.R.styleable.ScrollView_fillViewport, false));
    a.recycle();
  }

  @Override
  public boolean shouldDelayChildPressedState() {
    return true;
  }

  @Override
  protected float getTopFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getVerticalFadingEdgeLength();
    if (getScrollY() < length) {
      return getScrollY() / (float) length;
    }
    return 1.0f;
  }

  @Override
  protected float getBottomFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getVerticalFadingEdgeLength();
    final int bottomEdge = getHeight() - getPaddingBottom();
    final int span = getChildAt(0).getBottom() - getScrollY() - bottomEdge;
    if (span < length) {
      return span / (float) length;
    }
    return 1.0f;
  }

  @Override
  protected float getLeftFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getHorizontalFadingEdgeLength();
    if (getScrollX() < length) {
      return getScrollX() / (float) length;
    }
    return 1.0f;
  }

  @Override
  protected float getRightFadingEdgeStrength() {
    if (getChildCount() == 0) {
      return 0.0f;
    }
    final int length = getHorizontalFadingEdgeLength();
    final int rightEdge = getWidth() - getPaddingRight();
    final int span = getChildAt(0).getRight() - getScrollX() - rightEdge;
    if (span < length) {
      return span / (float) length;
    }
    return 1.0f;
  }

  public int getMaxVerticalScrollAmount() {
    return (int) (MAX_SCROLL_FACTOR * (getBottom() - getTop()));
  }

  public int getMaxHorizontalScrollAmount() {
    return (int) (MAX_SCROLL_FACTOR * (getRight() - getLeft()));
  }

  private void initScrollView() {
    mScroller = new OverScroller(getContext());
    setFocusable(true);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    setWillNotDraw(false);
    final ViewConfiguration configuration = ViewConfiguration.get(getContext());
    mTouchSlop = configuration.getScaledTouchSlop();
    mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
    mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    mOverscrollDistance = configuration.getScaledOverscrollDistance();
    mOverflingDistance = configuration.getScaledOverflingDistance();
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

  protected int getConstrainedScrollX(int x) {
    return Math.max(getScrollMinX(), Math.min(x, getScrollLimitX()));
  }

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

  public boolean isFillViewport() {
    return mFillViewport;
  }

  public void setFillViewport(boolean fillViewport) {
    if (fillViewport != mFillViewport) {
      mFillViewport = fillViewport;
      requestLayout();
    }
  }

  public boolean isSmoothScrollingEnabled() {
    return mSmoothScrollingEnabled;
  }

  public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
    mSmoothScrollingEnabled = smoothScrollingEnabled;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (!mFillViewport) {
      return;
    }
    final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    if (heightMode == MeasureSpec.UNSPECIFIED || widthMode == MeasureSpec.UNSPECIFIED) {
      return;
    }
    if (getChildCount() > 0) {
      final View child = getChild();
      int height = getMeasuredHeight();
      int width = getMeasuredWidth();
      final FrameLayout.LayoutParams lp = (LayoutParams) child.getLayoutParams();
      if (child.getMeasuredHeight() < height || child.getMeasuredWidth() < width) {
        int childWidthMeasureSpec;
        int childHeightMeasureSpec;
        if (child.getMeasuredHeight() < height) {
          height -= getPaddingTop();
          height -= getPaddingBottom();
          childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        } else {
          childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, getPaddingTop() + getPaddingBottom(), lp.height);
        }
        if (child.getMeasuredWidth() < width) {
          width -= getPaddingLeft();
          width -= getPaddingRight();
          childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        } else {
          childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, getPaddingLeft() + getPaddingRight(), lp.width);
        }
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
      }
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return super.dispatchKeyEvent(event) || executeKeyEvent(event);
  }

  public boolean executeKeyEvent(KeyEvent event) {
    mTempRect.setEmpty();
    if (!canScroll()) {
      if (isFocused() && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
        View currentFocused = findFocus();
        if (currentFocused == this)
          currentFocused = null;
        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, View.FOCUS_DOWN);
        return nextFocused != null && nextFocused != this && nextFocused.requestFocus(View.FOCUS_DOWN);
      }
      return false;
    }
    boolean handled = false;
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_UP:
          if (!event.isAltPressed()) {
            handled = arrowScroll(View.FOCUS_UP);
          } else {
            handled = fullScroll(View.FOCUS_UP);
          }
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          if (!event.isAltPressed()) {
            handled = arrowScroll(View.FOCUS_DOWN);
          } else {
            handled = fullScroll(View.FOCUS_DOWN);
          }
          break;
        case KeyEvent.KEYCODE_SPACE:
          pageScroll(event.isShiftPressed() ? View.FOCUS_UP : View.FOCUS_DOWN);
          break;
      }
    }
    return handled;
  }

  private boolean inChild(int x, int y) {
    if (getChildCount() > 0) {
      final int scrollY = getScrollY();
      final int scrollX = getScrollX();
      final View child = getChild();
      return !(y < child.getTop() - scrollY
          || y >= child.getBottom() - scrollY
          || x < child.getLeft() - scrollX
          || x >= child.getRight() - scrollX);
    }
    return false;
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
  public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    if (disallowIntercept) {
      recycleVelocityTracker();
    }
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    final int action = event.getAction();
    if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
      return true;
    }
    if (!canScroll()) {
      return false;
    }
    switch (action & MotionEvent.ACTION_MASK) {
      case MotionEvent.ACTION_MOVE: {
        final int activePointerId = mActivePointerId;
        if (activePointerId == INVALID_POINTER) {
          break;
        }
        final int pointerIndex = event.findPointerIndex(activePointerId);
        if (pointerIndex == -1) {
          Log.e(TAG, "Invalid pointerId=" + activePointerId + " in onInterceptTouchEvent");
          break;
        }
        final int y = (int) event.getY(pointerIndex);
        final int yDiff = Math.abs(y - mLastMotionY);
        if (yDiff > mTouchSlop) {
          mIsBeingDragged = true;
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
        final int y = (int) event.getY();
        if (!inChild((int) event.getX(), (int) y)) {
          mIsBeingDragged = false;
          recycleVelocityTracker();
          break;
        }
        /*
         * Remember location of down touch.
         * ACTION_DOWN always refers to pointer index 0.
         */
        mLastMotionY = y;
        mActivePointerId = event.getPointerId(0);
        initOrResetVelocityTracker();
        mVelocityTracker.addMovement(event);
        /*
         * If being flinged and user touches the screen, initiate drag;
         * otherwise don't.  mScroller.isFinished should be false when
         * being flinged.
         */
        mIsBeingDragged = !mScroller.isFinished();
        break;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        /* Release the drag */
        mIsBeingDragged = false;
        mActivePointerId = INVALID_POINTER;
        recycleVelocityTracker();
        if (mScroller.springBack(getScrollX(), getScrollY(), 0, 0, 0, getScrollRange())) {
          postInvalidateOnAnimation();
        }
        break;
      case MotionEvent.ACTION_POINTER_UP:
        onSecondaryPointerUp(event);
        break;
    }
    /*
     * The only time we want to intercept motion events is if we are in the
     * drag mode.
     */
    return mIsBeingDragged;
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev) {
    initVelocityTrackerIfNotExists();
    mVelocityTracker.addMovement(ev);
    final int action = ev.getAction();
    switch (action & MotionEvent.ACTION_MASK) {
      case MotionEvent.ACTION_DOWN: {
        if (getChildCount() == 0) {
          return false;
        }
        if ((mIsBeingDragged = !mScroller.isFinished())) {
          final ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
        }
        /*
         * If being flinged and user touches, stop the fling. isFinished
         * will be false if being flinged.
         */
        if (!mScroller.isFinished()) {
          mScroller.abortAnimation();
        }
        // Remember where the motion event started
        mLastMotionY = (int) ev.getY();
        mActivePointerId = ev.getPointerId(0);
        break;
      }
      case MotionEvent.ACTION_MOVE:
        final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
        if (activePointerIndex == -1) {
          Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
          break;
        }
        final int y = (int) ev.getY(activePointerIndex);
        int deltaY = mLastMotionY - y;
        if (!mIsBeingDragged && Math.abs(deltaY) > mTouchSlop) {
          final ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
          mIsBeingDragged = true;
          if (deltaY > 0) {
            deltaY -= mTouchSlop;
          } else {
            deltaY += mTouchSlop;
          }
        }
        if (mIsBeingDragged) {
          // Scroll to follow the motion event
          mLastMotionY = y;
          final int oldX = getScrollX();
          final int oldY = getScrollY();
          final int range = getScrollRange();
          final int overscrollMode = getOverScrollMode();
          final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);
          // Calling overScrollBy will call onOverScrolled, which
          // calls onScrollChanged if applicable.
          if (overScrollBy(0, deltaY, 0, getScrollY(), 0, range, 0, mOverscrollDistance, true)) {
            // Break our velocity if we hit a scroll barrier.
            mVelocityTracker.clear();
          }
          if (canOverscroll) {
            final int pulledToY = oldY + deltaY;
            if (pulledToY < 0) {
              mEdgeGlowTop.onPull((float) deltaY / getHeight());
              if (!mEdgeGlowBottom.isFinished()) {
                mEdgeGlowBottom.onRelease();
              }
            } else if (pulledToY > range) {
              mEdgeGlowBottom.onPull((float) deltaY / getHeight());
              if (!mEdgeGlowTop.isFinished()) {
                mEdgeGlowTop.onRelease();
              }
            }
            if (mEdgeGlowTop != null && (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished())) {
              postInvalidateOnAnimation();
            }
          }
        }
        break;
      case MotionEvent.ACTION_UP:
        if (mIsBeingDragged) {
          final VelocityTracker velocityTracker = mVelocityTracker;
          velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
          int initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);
          if (getChildCount() > 0) {
            if ((Math.abs(initialVelocity) > mMinimumVelocity)) {
              fling(-initialVelocity);
            } else {
              if (mScroller.springBack(getScrollX(), getScrollY(), 0, 0, 0, getScrollRange())) {
                postInvalidateOnAnimation();
              }
            }
          }
          mActivePointerId = INVALID_POINTER;
          endDrag();
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        if (mIsBeingDragged && getChildCount() > 0) {
          if (mScroller.springBack(getScrollX(), getScrollY(), 0, 0, 0, getScrollRange())) {
            postInvalidateOnAnimation();
          }
          mActivePointerId = INVALID_POINTER;
          endDrag();
        }
        break;
      case MotionEvent.ACTION_POINTER_DOWN: {
        final int index = ev.getActionIndex();
        mLastMotionY = (int) ev.getY(index);
        mActivePointerId = ev.getPointerId(index);
        break;
      }
      case MotionEvent.ACTION_POINTER_UP:
        onSecondaryPointerUp(ev);
        mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
        break;
    }
    return true;
  }

  private void onSecondaryPointerUp(MotionEvent ev) {
    final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    final int pointerId = ev.getPointerId(pointerIndex);
    if (pointerId == mActivePointerId) {
      // This was our active pointer going up. Choose a new
      // active pointer and adjust accordingly.
      // TODO: Make this decision more intelligent.
      final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
      mLastMotionY = (int) ev.getY(newPointerIndex);
      mActivePointerId = ev.getPointerId(newPointerIndex);
      if (mVelocityTracker != null) {
        mVelocityTracker.clear();
      }
    }
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
      switch (event.getAction()) {
        case MotionEvent.ACTION_SCROLL: {
          if (!mIsBeingDragged) {
            final float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (vscroll != 0) {
              final int range = getScrollRange();
              int oldScrollY = getScrollY();
              int newScrollY = (int) (oldScrollY - vscroll);
              if (newScrollY < 0) {
                newScrollY = 0;
              } else if (newScrollY > range) {
                newScrollY = range;
              }
              if (newScrollY != oldScrollY) {
                super.scrollTo(getScrollX(), newScrollY);
                return true;
              }
            }
          }
        }
      }
    }
    return super.onGenericMotionEvent(event);
  }

  @Override
  protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
    // Treat animating scrolls differently; see #computeScroll() for why.
    if (!mScroller.isFinished()) {
      final int oldX = getScrollX();
      final int oldY = getScrollY();
      setScrollX(scrollX);
      setScrollY(scrollY);
      invalidate();
      onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
      if (clampedY) {
        mScroller.springBack(getScrollX(), getScrollY(), 0, 0, 0, getScrollRange());
      }
    } else {
      super.scrollTo(scrollX, scrollY);
    }
    awakenScrollBars();
  }

  @Override
  public boolean performAccessibilityAction(int action, Bundle arguments) {
    if (super.performAccessibilityAction(action, arguments)) {
      return true;
    }
    if (!isEnabled()) {
      return false;
    }
    switch (action) {
      case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
        final int viewportHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        final int targetScrollY = Math.min(getScrollY() + viewportHeight, getScrollRange());
        if (targetScrollY != getScrollY()) {
          smoothScrollTo(0, targetScrollY);
          return true;
        }
      }
      return false;
      case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
        final int viewportHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        final int targetScrollY = Math.max(getScrollY() - viewportHeight, 0);
        if (targetScrollY != getScrollY()) {
          smoothScrollTo(0, targetScrollY);
          return true;
        }
      }
      return false;
    }
    return false;
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(ScrollView.class.getName());
    if (isEnabled()) {
      final int scrollRange = getScrollRange();
      if (scrollRange > 0) {
        info.setScrollable(true);
        if (getScrollY() > 0) {
          info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        if (getScrollY() < scrollRange) {
          info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
      }
    }
  }

  @Override
  public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
    super.onInitializeAccessibilityEvent(event);
    event.setClassName(ScrollView.class.getName());
    final boolean scrollable = getScrollRange() > 0;
    event.setScrollable(scrollable);
    event.setScrollX(getScrollX());
    event.setScrollY(getScrollY());
    event.setMaxScrollX(getScrollX());
    event.setMaxScrollY(getScrollRange());
  }

  private int getScrollRange() {
    int scrollRange = 0;
    if (getChildCount() > 0) {
      View child = getChild();
      scrollRange = Math.max(0, child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
    }
    return scrollRange;
  }

  private View findFocusableViewInBounds(boolean topFocus, int top, int bottom) {
    List<View> focusables = getFocusables(View.FOCUS_FORWARD);
    View focusCandidate = null;
    /*
     * A fully contained focusable is one where its top is below the bound's
     * top, and its bottom is above the bound's bottom. A partially
     * contained focusable is one where some part of it is within the
     * bounds, but it also has some part that is not within bounds.  A fully contained
     * focusable is preferred to a partially contained focusable.
     */
    boolean foundFullyContainedFocusable = false;
    int count = focusables.size();
    for (int i = 0; i < count; i++) {
      View view = focusables.get(i);
      int viewTop = view.getTop();
      int viewBottom = view.getBottom();
      if (top < viewBottom && viewTop < bottom) {
        /*
         * the focusable is in the target area, it is a candidate for
         * focusing
         */
        final boolean viewIsFullyContained = (top < viewTop) && (viewBottom < bottom);
        if (focusCandidate == null) {
          /* No candidate, take this one */
          focusCandidate = view;
          foundFullyContainedFocusable = viewIsFullyContained;
        } else {
          final boolean viewIsCloserToBoundary = (topFocus && viewTop < focusCandidate.getTop()) || (!topFocus && viewBottom > focusCandidate.getBottom());
          if (foundFullyContainedFocusable) {
            if (viewIsFullyContained && viewIsCloserToBoundary) {
              /*
               * We're dealing with only fully contained views, so
               * it has to be closer to the boundary to beat our
               * candidate
               */
              focusCandidate = view;
            }
          } else {
            if (viewIsFullyContained) {
              /* Any fully contained view beats a partially contained view */
              focusCandidate = view;
              foundFullyContainedFocusable = true;
            } else if (viewIsCloserToBoundary) {
              /*
               * Partially contained view beats another partially
               * contained view if it's closer
               */
              focusCandidate = view;
            }
          }
        }
      }
    }
    return focusCandidate;
  }

  public boolean pageScroll(int direction) {
    boolean down = direction == View.FOCUS_DOWN;
    int height = getHeight();
    if (down) {
      mTempRect.top = getScrollY() + height;
      int count = getChildCount();
      if (count > 0) {
        View view = getChildAt(count - 1);
        if (mTempRect.top + height > view.getBottom()) {
          mTempRect.top = view.getBottom() - height;
        }
      }
    } else {
      mTempRect.top = getScrollY() - height;
      if (mTempRect.top < 0) {
        mTempRect.top = 0;
      }
    }
    mTempRect.bottom = mTempRect.top + height;
    return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);
  }

  public boolean fullScroll(int direction) {
    boolean down = direction == View.FOCUS_DOWN;
    int height = getHeight();
    mTempRect.top = 0;
    mTempRect.bottom = height;
    if (down) {
      int count = getChildCount();
      if (count > 0) {
        View view = getChildAt(count - 1);
        mTempRect.bottom = view.getBottom() + getPaddingBottom();
        mTempRect.top = mTempRect.bottom - height;
      }
    }
    return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);
  }

  private boolean scrollAndFocus(int direction, int top, int bottom) {
    boolean handled = true;
    int height = getHeight();
    int containerTop = getScrollY();
    int containerBottom = containerTop + height;
    boolean up = direction == View.FOCUS_UP;
    View newFocused = findFocusableViewInBounds(up, top, bottom);
    if (newFocused == null) {
      newFocused = this;
    }
    if (top >= containerTop && bottom <= containerBottom) {
      handled = false;
    } else {
      int delta = up ? (top - containerTop) : (bottom - containerBottom);
      doScrollY(delta);
    }
    if (newFocused != findFocus())
      newFocused.requestFocus(direction);
    return handled;
  }

  public boolean arrowScroll(int direction) {
    View currentFocused = findFocus();
    if (currentFocused == this)
      currentFocused = null;
    View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);
    final int maxJump = getMaxVerticalScrollAmount();
    if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJump, getHeight())) {
      nextFocused.getDrawingRect(mTempRect);
      offsetDescendantRectToMyCoords(nextFocused, mTempRect);
      int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
      doScrollY(scrollDelta);
      nextFocused.requestFocus(direction);
    } else {
      // no new focus
      int scrollDelta = maxJump;
      if (direction == View.FOCUS_UP && getScrollY() < scrollDelta) {
        scrollDelta = getScrollY();
      } else if (direction == View.FOCUS_DOWN) {
        if (getChildCount() > 0) {
          int daBottom = getChild().getBottom();
          int screenBottom = getScrollY() + getHeight() - getPaddingBottom();
          if (daBottom - screenBottom < maxJump) {
            scrollDelta = daBottom - screenBottom;
          }
        }
      }
      if (scrollDelta == 0) {
        return false;
      }
      doScrollY(direction == View.FOCUS_DOWN ? scrollDelta : -scrollDelta);
    }
    if (currentFocused != null && currentFocused.isFocused() && isOffScreen(currentFocused)) {
      // previously focused item still has focus and is off screen, give
      // it up (take it back to ourselves)
      // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
      // sure to
      // get it)
      final int descendantFocusability = getDescendantFocusability();  // save
      setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
      requestFocus();
      setDescendantFocusability(descendantFocusability);  // restore
    }
    return true;
  }

  private boolean isOffScreen(View descendant) {
    return !isWithinDeltaOfScreen(descendant, 0, getHeight());
  }

  private boolean isWithinDeltaOfScreen(View descendant, int delta, int height) {
    descendant.getDrawingRect(mTempRect);
    offsetDescendantRectToMyCoords(descendant, mTempRect);
    return (mTempRect.bottom + delta) >= getScrollY() && (mTempRect.top - delta) <= (getScrollY() + height);
  }

  private void doScrollY(int delta) {
    if (delta != 0) {
      if (mSmoothScrollingEnabled) {
        smoothScrollBy(0, delta);
      } else {
        scrollBy(0, delta);
      }
    }
  }

  public final void smoothScrollBy(int dx, int dy) {
    if (getChildCount() == 0) {
      // Nothing to do.
      return;
    }
    long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
    if (duration > ANIMATED_SCROLL_GAP) {
      final int height = getHeight() - getPaddingBottom() - getPaddingTop();
      final int bottom = getChild().getHeight();
      final int maxY = Math.max(0, bottom - height);
      final int scrollY = getScrollY();
      dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;
      mScroller.startScroll(getScrollX(), scrollY, 0, dy);
      postInvalidateOnAnimation();
    } else {
      if (!mScroller.isFinished()) {
        mScroller.abortAnimation();
      }
      scrollBy(dx, dy);
    }
    mLastScroll = AnimationUtils.currentAnimationTimeMillis();
  }

  public final void smoothScrollTo(int x, int y) {
    smoothScrollBy(x - getScrollX(), y - getScrollY());
  }

  @Override
  protected int computeVerticalScrollRange() {
    final int count = getChildCount();
    final int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
    if (count == 0) {
      return contentHeight;
    }
    int scrollRange = getChild().getBottom();
    final int scrollY = getScrollY();
    final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
    if (scrollY < 0) {
      scrollRange -= scrollY;
    } else if (scrollY > overscrollBottom) {
      scrollRange += scrollY - overscrollBottom;
    }
    return scrollRange;
  }

  @Override
  protected int computeVerticalScrollOffset() {
    return Math.max(0, super.computeVerticalScrollOffset());
  }

  @Override
  protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
    ViewGroup.LayoutParams lp = child.getLayoutParams();
    int childWidthMeasureSpec;
    int childHeightMeasureSpec;
    childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight(), lp.width);
    childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  @Override
  protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
    final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
    final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
    final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);
    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
  }

  @Override
  public void computeScroll() {
    if (mScroller.computeScrollOffset()) {
      // This is called at drawing time by ViewGroup.  We don't want to
      // re-show the scrollbars at this point, which scrollTo will do,
      // so we replicate most of scrollTo here.
      //
      //         It's a little odd to call onScrollChanged from inside the drawing.
      //
      //         It is, except when you remember that computeScroll() is used to
      //         animate scrolling. So unless we want to defer the onScrollChanged()
      //         until the end of the animated scrolling, we don't really have a
      //         choice here.
      //
      //         I agree.  The alternative, which I think would be worse, is to post
      //         something and tell the subclasses later.  This is bad because there
      //         will be a window where getScrollX()/Y is different from what the app
      //         thinks it is.
      //
      int oldX = getScrollX();
      int oldY = getScrollY();
      int x = mScroller.getCurrX();
      int y = mScroller.getCurrY();
      if (oldX != x || oldY != y) {
        final int range = getScrollRange();
        final int overscrollMode = getOverScrollMode();
        final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);
        overScrollBy(x - oldX, y - oldY, oldX, oldY, 0, range, 0, mOverflingDistance, false);
        onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
        if (canOverscroll) {
          if (y < 0 && oldY >= 0) {
            mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
          } else if (y > range && oldY <= range) {
            mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
          }
        }
      }
      if (!awakenScrollBars()) {
        // Keep on drawing until the animation has finished.
        postInvalidateOnAnimation();
      }
    }
  }

  private void scrollToChild(View child) {
    child.getDrawingRect(mTempRect);
    /* Offset from child's local coordinates to ScrollView coordinates */
    offsetDescendantRectToMyCoords(child, mTempRect);
    int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
    if (scrollDelta != 0) {
      scrollBy(0, scrollDelta);
    }
  }

  private boolean scrollToChildRect(Rect rect, boolean immediate) {
    final int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
    final boolean scroll = delta != 0;
    if (scroll) {
      if (immediate) {
        scrollBy(0, delta);
      } else {
        smoothScrollBy(0, delta);
      }
    }
    return scroll;
  }

  protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
    if (getChildCount() == 0)
      return 0;
    int height = getHeight();
    int screenTop = getScrollY();
    int screenBottom = screenTop + height;
    int fadingEdge = getVerticalFadingEdgeLength();
    // leave room for top fading edge as long as rect isn't at very top
    if (rect.top > 0) {
      screenTop += fadingEdge;
    }
    // leave room for bottom fading edge as long as rect isn't at very bottom
    if (rect.bottom < getChild().getHeight()) {
      screenBottom -= fadingEdge;
    }
    int scrollYDelta = 0;
    if (rect.bottom > screenBottom && rect.top > screenTop) {
      // need to move down to get it in view: move down just enough so
      // that the entire rectangle is in view (or at least the first
      // screen size chunk).
      if (rect.height() > height) {
        // just enough to get screen size chunk on
        scrollYDelta += (rect.top - screenTop);
      } else {
        // get entire rect at bottom of screen
        scrollYDelta += (rect.bottom - screenBottom);
      }
      // make sure we aren't scrolling beyond the end of our content
      int bottom = getChild().getBottom();
      int distanceToBottom = bottom - screenBottom;
      scrollYDelta = Math.min(scrollYDelta, distanceToBottom);
    } else if (rect.top < screenTop && rect.bottom < screenBottom) {
      // need to move up to get it in view: move up just enough so that
      // entire rectangle is in view (or at least the first screen
      // size chunk of it).
      if (rect.height() > height) {
        // screen size chunk
        scrollYDelta -= (screenBottom - rect.bottom);
      } else {
        // entire rect at top
        scrollYDelta -= (screenTop - rect.top);
      }
      // make sure we aren't scrolling any further than the top our content
      scrollYDelta = Math.max(scrollYDelta, -getScrollY());
    }
    return scrollYDelta;
  }

  @Override
  public void requestChildFocus(View child, View focused) {
    if (!mIsLayoutDirty) {
      scrollToChild(focused);
    } else {
      // The child may not be laid out yet, we can't compute the scroll yet
      mChildToScrollTo = focused;
    }
    super.requestChildFocus(child, focused);
  }

  @Override
  protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
    // convert from forward / backward notation to up / down / left / right
    // (ugh).
    if (direction == View.FOCUS_FORWARD) {
      direction = View.FOCUS_DOWN;
    } else if (direction == View.FOCUS_BACKWARD) {
      direction = View.FOCUS_UP;
    }
    final View nextFocus = previouslyFocusedRect == null ? FocusFinder.getInstance().findNextFocus(this, null, direction) : FocusFinder.getInstance().findNextFocusFromRect(this, previouslyFocusedRect, direction);
    if (nextFocus == null) {
      return false;
    }
    if (isOffScreen(nextFocus)) {
      return false;
    }
    return nextFocus.requestFocus(direction, previouslyFocusedRect);
  }

  @Override
  public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
    // offset into coordinate space of this scroll view
    rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
    return scrollToChildRect(rectangle, immediate);
  }

  @Override
  public void requestLayout() {
    mIsLayoutDirty = true;
    super.requestLayout();
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    mIsLayoutDirty = false;
    // Give a child focus if it needs it
    if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
      scrollToChild(mChildToScrollTo);
    }
    mChildToScrollTo = null;
    if (!isLaidOut()) {
      if (mSavedState != null) {
        setScrollY(mSavedState.scrollPosition);
        mSavedState = null;
      } // getScrollY() default value is "0"
      final int childHeight = (getChildCount() > 0) ? getChild().getMeasuredHeight() : 0;
      final int scrollRange = Math.max(0, childHeight - (b - t - getPaddingBottom() - getPaddingTop()));
      // Don't forget to clamp
      if (getScrollY() > scrollRange) {
        setScrollY(scrollRange);
      } else if (getScrollY() < 0) {
        setScrollY(0);
      }
    }
    // Calling this with the present values causes it to re-claim them
    scrollTo(getScrollX(), getScrollY());
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    View currentFocused = findFocus();
    if (null == currentFocused || this == currentFocused)
      return;
    // If the currently-focused view was visible on the screen when the
    // screen was at the old height, then scroll the screen to make that
    // view visible with the new screen height.
    if (isWithinDeltaOfScreen(currentFocused, 0, oldh)) {
      currentFocused.getDrawingRect(mTempRect);
      offsetDescendantRectToMyCoords(currentFocused, mTempRect);
      int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
      doScrollY(scrollDelta);
    }
  }

  private static boolean isViewDescendantOf(View child, View parent) {
    if (child == parent) {
      return true;
    }
    final ViewParent theParent = child.getParent();
    return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
  }

  public void fling(int velocityY) {
    if (getChildCount() > 0) {
      int height = getHeight() - getPaddingBottom() - getPaddingTop();
      int bottom = getChild().getHeight();
      mScroller.fling(getScrollX(), getScrollY(), 0, velocityY, 0, 0, 0, Math.max(0, bottom - height), 0, height / 2);
      postInvalidateOnAnimation();
    }
  }

  private void endDrag() {
    mIsBeingDragged = false;
    recycleVelocityTracker();
    if (mEdgeGlowTop != null) {
      mEdgeGlowTop.onRelease();
      mEdgeGlowBottom.onRelease();
    }
  }

  @Override
  public void scrollTo(int x, int y) {
    // we rely on the fact the View.scrollBy calls scrollTo.
    if (getChildCount() > 0) {
      View child = getChild();
      x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
      y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
      if (x != getScrollX() || y != getScrollY()) {
        super.scrollTo(x, y);
      }
    }
  }

  @Override
  public void setOverScrollMode(int mode) {
    if (mode != OVER_SCROLL_NEVER) {
      if (mEdgeGlowTop == null) {
        Context context = getContext();
        mEdgeGlowTop = new EdgeEffect(context);
        mEdgeGlowBottom = new EdgeEffect(context);
      }
    } else {
      mEdgeGlowTop = null;
      mEdgeGlowBottom = null;
    }
    super.setOverScrollMode(mode);
  }

  @Override
  public void draw(Canvas canvas) {
    super.draw(canvas);
    if (mEdgeGlowTop != null) {
      final int scrollY = getScrollY();
      if (!mEdgeGlowTop.isFinished()) {
        final int restoreCount = canvas.save();
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        canvas.translate(getPaddingLeft(), Math.min(0, scrollY));
        mEdgeGlowTop.setSize(width, getHeight());
        if (mEdgeGlowTop.draw(canvas)) {
          postInvalidateOnAnimation();
        }
        canvas.restoreToCount(restoreCount);
      }
      if (!mEdgeGlowBottom.isFinished()) {
        final int restoreCount = canvas.save();
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        final int height = getHeight();
        canvas.translate(-width + getPaddingLeft(), Math.max(getScrollRange(), scrollY) + height);
        canvas.rotate(180, width, 0);
        mEdgeGlowBottom.setSize(width, height);
        if (mEdgeGlowBottom.draw(canvas)) {
          postInvalidateOnAnimation();
        }
        canvas.restoreToCount(restoreCount);
      }
    }
  }

  private static int clamp(int n, int my, int child) {
    if (my >= child || n < 0) {
      /* my >= child is this case:
       *                    |--------------- me ---------------|
       *     |------ child ------|
       * or
       *     |--------------- me ---------------|
       *            |------ child ------|
       * or
       *     |--------------- me ---------------|
       *                                  |------ child ------|
       *
       * n < 0 is this case:
       *     |------ me ------|
       *                    |-------- child --------|
       *     |-- getScrollX() --|
       */
      return 0;
    }
    if ((my + n) > child) {
      /* this case:
       *                    |------ me ------|
       *     |------ child ------|
       *     |-- getScrollX() --|
       */
      return child - my;
    }
    return n;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (getContext().getApplicationInfo().targetSdkVersion <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      // Some old apps reused IDs in ways they shouldn't have.
      // Don't break them, but they don't get scroll state restoration.
      super.onRestoreInstanceState(state);
      return;
    }
    SavedState ss = (SavedState) state;
    super.onRestoreInstanceState(ss.getSuperState());
    mSavedState = ss;
    requestLayout();
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    if (getContext().getApplicationInfo().targetSdkVersion <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      // Some old apps reused IDs in ways they shouldn't have.
      // Don't break them, but they don't get scroll state restoration.
      return super.onSaveInstanceState();
    }
    Parcelable superState = super.onSaveInstanceState();
    SavedState ss = new SavedState(superState);
    ss.scrollPosition = getScrollY();
    return ss;
  }

  static class SavedState extends BaseSavedState {
    public int scrollPosition;

    SavedState(Parcelable superState) {
      super(superState);
    }

    public SavedState(Parcel source) {
      super(source);
      scrollPosition = source.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      super.writeToParcel(dest, flags);
      dest.writeInt(scrollPosition);
    }

    @Override
    public String toString() {
      return "ScrollView.SavedState{" + Integer.toHexString(System.identityHashCode(this)) + " scrollPosition=" + scrollPosition + "}";
    }

    public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
      public SavedState createFromParcel(Parcel in) {
        return new SavedState(in);
      }

      public SavedState[] newArray(int size) {
        return new SavedState[size];
      }
    };
  }
}