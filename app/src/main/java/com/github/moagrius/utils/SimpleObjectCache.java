package com.github.moagrius.utils;

import java.lang.ref.SoftReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SimpleObjectCache<T> {

  private Class<T> mClass;
  private Queue<SoftReference<T>> mQueue = new ConcurrentLinkedQueue<>();

  public SimpleObjectCache(Class<T> clazz) {
    mClass = clazz;
  }

  public synchronized T get() {
    if (mQueue.peek() != null) {
      T t = mQueue.poll().get();
      if (t != null) {
         return t;
      }
    }
    try {
      return mClass.newInstance();
    } catch (InstantiationException e) {
      // no op
    } catch (IllegalAccessException e) {
      // no op
    }
    return null;
  }

  public void put(T t) {
    if (t != null) {
      mQueue.add(new SoftReference<>(t));
    }
  }

}
