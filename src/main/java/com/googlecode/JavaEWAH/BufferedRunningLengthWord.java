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

/**
 * This code is untested as of Jan. 30th 2009.
 */
public class BufferedRunningLengthWord {

  public BufferedRunningLengthWord(RunningLengthWord rlw) {
    val = rlw.array[rlw.position];
  }
  public BufferedRunningLengthWord(long a) {
    val = a;
  }
  public long getNumberOfLiteralWords() {
    return  val >>> (1+runninglengthbits);
  }
  public void setNumberOfLiteralWords(long number) {
    val |= notrunninglengthplusrunningbit;
    val &= (number << (runninglengthbits +1) ) |runninglengthplusrunningbit;
  }
  public void setRunningBit(boolean b) {
    if(b) val |= 1l;
    else val &= ~1l;
  }
  public boolean getRunningBit() {
    return (val & 1) != 0;
  }
  public long getRunningLength() {
    return (val >>> 1) & largestrunninglengthcount ;
  }
  public void setRunningLength(long number) {
    val |= shiftedlargestrunninglengthcount;
    val &= (number << 1) | notshiftedlargestrunninglengthcount;
  }

  public long  size() {
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

  public long val;
  public static final int runninglengthbits = 32;
//		public static int literalbits = 64 - 1 - runninglengthbits;
//		public static long largestliteralcount = (1l<<literalbits) - 1;
  public static final long largestrunninglengthcount = (1l<<runninglengthbits)-1;
  public static final long shiftedlargestrunninglengthcount = largestrunninglengthcount<<1;
  public static final long notshiftedlargestrunninglengthcount = ~shiftedlargestrunninglengthcount;
  public static final long runninglengthplusrunningbit = (1l<<(runninglengthbits+1)) - 1;
  public static final long notrunninglengthplusrunningbit =~runninglengthplusrunningbit;
//		public static long notlargestrunninglengthcount =~largestrunninglengthcount;
}