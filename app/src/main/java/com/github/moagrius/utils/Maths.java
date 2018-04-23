package com.github.moagrius.utils;

public class Maths {

  public static final double LOG_2 = Math.log(2);

  public static double log2(int n) {
    return Math.log(n) / LOG_2;
  }

  public static int roundWithStep(float value, int step) {
    return Math.round(value / step) * step;
  }

  public static int roundUpWithStep(float value, int step) {
    return (int) Math.ceil(value / step) * step;
  }

  public static int roundDownWithStep(float value, int step) {
    return (int) Math.floor(value / step) * step;
  }

}
