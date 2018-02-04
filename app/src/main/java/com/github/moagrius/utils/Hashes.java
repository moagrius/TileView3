package com.github.moagrius.utils;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class Hashes {

  public static int compute(int initialNonZeroOddNumber, int multiplierNonZeroOddNumber, Object... members) {
    for (Object member : members) {
      initialNonZeroOddNumber = multiplierNonZeroOddNumber * initialNonZeroOddNumber + (member == null ? 0 : member.hashCode());
    }
    return initialNonZeroOddNumber;
  }

}
