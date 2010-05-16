/*
 * Copyright 2010 Daniel Lemire
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.googlecode.JavaEWAH;

public class RunningLengthWord {

  RunningLengthWord(long[] a, int p) {
    array = a;
    position = p;
  }

  public long getNumberOfLiteralWords() {
    return  array[position] >>> (1+runninglengthbits);
  }

  public void setNumberOfLiteralWords(long number) {
    array[position] |= notrunninglengthplusrunningbit;
    array[position] &= (number << (runninglengthbits +1) ) |runninglengthplusrunningbit;
  }

  public void setRunningBit(boolean b) {
    if(b) array[position] |= 1l;
    else array[position] &= ~1l;
  }

  public boolean getRunningBit() {
    return (array[position] & 1) != 0;
  }

  public long getRunningLength() {
    return (array[position] >>> 1) & largestrunninglengthcount ;
  }

  public void setRunningLength(long number) {
    array[position] |= shiftedlargestrunninglengthcount;
    array[position] &= (number << 1) | notshiftedlargestrunninglengthcount;
  }

  public long size() {
    return getRunningLength() + getNumberOfLiteralWords();
  }

  public String toString() {
    return "running bit = "+getRunningBit() +" running length = "+getRunningLength() + " number of lit. words "+ getNumberOfLiteralWords();
  }

  public void discardFirstWords(long x) {
    long rl = getRunningLength() ;
    if(rl >= x) {
      setRunningLength(rl - x);
      assert getRunningLength() == rl-x;
      return;
    }
    x -= rl;
    setRunningLength(0);
    assert getRunningLength() == 0;
    long old = getNumberOfLiteralWords() ;
    assert old >= x;
    setNumberOfLiteralWords(old - x);
    assert old-x == getNumberOfLiteralWords();
  }

  public long[] array;
  public int position;
  public static final int runninglengthbits = 32;
  public static final int literalbits = 64 - 1 - runninglengthbits;
  public static final long largestliteralcount = (1l<<literalbits) - 1;
  public static final long largestrunninglengthcount = (1l<<runninglengthbits)-1;
  public static final long shiftedlargestrunninglengthcount = largestrunninglengthcount<<1;
  public static final long notshiftedlargestrunninglengthcount = ~shiftedlargestrunninglengthcount;
  public static final long runninglengthplusrunningbit = (1l<<(runninglengthbits+1)) - 1;
  public static final long notrunninglengthplusrunningbit =~runninglengthplusrunningbit;
//  public static long notlargestrunninglengthcount =~largestrunninglengthcount;
}