package com.github.moagrius.utils;

import java.lang.ref.SoftReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SimpleObjectPool<T> {

  private Factory<T> mFactory;
  private Queue<SoftReference<T>> mQueue = new ConcurrentLinkedQueue<>();

  public SimpleObjectPool(Factory<T> factory) {
    mFactory = factory;
  }

  public synchronized T get() {
    if (mQueue.peek() != null) {
      T t = mQueue.poll().get();
      if (t != null) {
        return t;
      }
    }
    return mFactory.create();
  }

  public synchronized void put(T t) {
    if (t != null) {
      mQueue.add(new SoftReference<>(t));
    }
  }

  public interface Factory<T> {
    T create();
  }

}