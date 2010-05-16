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
 * This implements the patent-free(*) EWAH scheme.
 * Roughly speaking, it is a 64-bit variant of the
 * BBC compression scheme used by Oracle for its bitmap
 * indexes.
 *
 * The objective of this compression type is to provide
 * some compression, while reducing as much as possible
 * the CPU cycle usage.
 *
 * This implementation being 64-bit, it assumes a 64-bit CPU
 * together with a 64-bit Java Virtual Machine. This same code
 * on a 32-bit machine may not be as fast.
 *
 * For more details, see the following paper:
 *
 * Daniel Lemire, Owen Kaser, Kamel Aouiche, Sorting improves
 * word-aligned bitmap indexes. Data & Knowledge
 * Engineering 69 (1), pages 3-28, 2010.
 * http://arxiv.org/abs/0901.3751
 *
 * It was first described by Wu et al. and named WBC:
 *
 * K. Wu, E. J. Otoo, A. Shoshani, H. Nordberg, Notes on design and
 * implementation of compressed bit vectors, Tech. Rep. LBNL/PUB-3161,
 * Lawrence Berkeley National Laboratory, available from http://crd.lbl.
 *  gov/~kewu/ps/PUB-3161.html (2001).
 *
 * *- The author (D. Lemire) does not know of any patent
 *    infringed by the following implementation. However, similar
 *    schemes, like WAH are covered by patents.
 */

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.Vector;

public class EWAHCompressedBitmap implements Cloneable, Externalizable {

  public EWAHCompressedBitmap () {}

  EWAHIterator getEWAHIterator() {
    return new EWAHIterator(buffer,actualsizeinwords);
  }

  public EWAHCompressedBitmap andNot(EWAHCompressedBitmap a) {
    try{
      EWAHCompressedBitmap nota = (EWAHCompressedBitmap) a.clone();
      nota.not();
      return and(nota);
    } catch (CloneNotSupportedException cnse) {
      throw new RuntimeException();
    }
  }

  public EWAHCompressedBitmap and(EWAHCompressedBitmap a) {
    EWAHCompressedBitmap container  = new EWAHCompressedBitmap();
    EWAHIterator i = a.getEWAHIterator();
    EWAHIterator j = getEWAHIterator();
    if(!(i.hasNext() && j.hasNext())) {// hopefully this never happens...
      container.setSizeInBits(sizeInBits());
      return container;
    }
    // at this point, this should be safe:
    BufferedRunningLengthWord rlwi = new BufferedRunningLengthWord(i.next());
    BufferedRunningLengthWord rlwj = new BufferedRunningLengthWord(j.next());
    while (true) {
      boolean i_is_prey = rlwi.size()<rlwj.size();
      BufferedRunningLengthWord prey = i_is_prey ? rlwi: rlwj;
      BufferedRunningLengthWord predator = i_is_prey ? rlwj: rlwi;
      if(!prey.getRunningBit()) {
        long preyrl = prey.getRunningLength();
        predator.discardFirstWords(preyrl);
        prey.discardFirstWords(preyrl);
        container.addStreamOfEmptyWords(false, preyrl);
      } else {
        // we have a stream of 1x11
        long predatorrl  = predator.getRunningLength();
        long preyrl  = prey.getRunningLength();
        long tobediscarded = (predatorrl >= preyrl) ?  preyrl : predatorrl;
        container.addStreamOfEmptyWords(predator.getRunningBit(), tobediscarded);
        int  dw_predator  = (i_is_prey ? j.dirtyWords(): i.dirtyWords());
        container.addStreamOfDirtyWords(buffer, dw_predator, preyrl - tobediscarded);
        predator.discardFirstWords(preyrl);
        prey.discardFirstWords(preyrl);
      }
      long predatorrl = predator.getRunningLength();
      if(predatorrl>0){
        if(!predator.getRunningBit()) {
          long nbre_dirty_prey = prey.getNumberOfLiteralWords();
          long tobediscarded = (predatorrl >= nbre_dirty_prey) ? nbre_dirty_prey : predatorrl;
          predator.discardFirstWords(tobediscarded);
          prey.discardFirstWords(tobediscarded);
          container.addStreamOfEmptyWords(false, tobediscarded);
        } else {
          long nbre_dirty_prey = prey.getNumberOfLiteralWords();
          int dw_prey = i_is_prey ? i.dirtyWords(): j.dirtyWords();
          long tobediscarded = (predatorrl >= nbre_dirty_prey) ? nbre_dirty_prey : predatorrl;
          //nbre_dirty_prey -= tobediscarded;
          container.addStreamOfDirtyWords(buffer, dw_prey, tobediscarded);
          //dw_prey += tobediscarded;
          predator.discardFirstWords(tobediscarded);
          prey.discardFirstWords(tobediscarded);
        }
      }
      // all that is left to do now is to AND the dirty words
      long nbre_dirty_prey = prey.getNumberOfLiteralWords();
      if(nbre_dirty_prey > 0) {
        for(int k = 0; k<nbre_dirty_prey;++k) {
          container.add(i.buffer()[i.dirtyWords()+k] & j.buffer()[i.dirtyWords()+k]);
        }
        predator.discardFirstWords(nbre_dirty_prey);
      }
      if( i_is_prey ) {
        if(!i.hasNext()) break;
        rlwi = new BufferedRunningLengthWord( i.next() );
      } else {
        if(!j.hasNext()) break;
        rlwj = new BufferedRunningLengthWord(j.next());
      }
    }
    container.setSizeInBits(sizeInBits());
    return container;
  }

  /*
     * return a negated version of this bitmap
     */
  public void not() {
    EWAHIterator i = new EWAHIterator(buffer,actualsizeinwords);
    while(i.hasNext()) {
      RunningLengthWord rlw = i.next();
      rlw.setRunningBit(! rlw.getRunningBit());
      for(int j = 0; j<rlw.getNumberOfLiteralWords();++j) {
        i.buffer()[i.dirtyWords()+j] = ~i.buffer()[i.dirtyWords()+j];
      }
    }
  }

  public EWAHCompressedBitmap or(EWAHCompressedBitmap a) {
    EWAHCompressedBitmap container  = new EWAHCompressedBitmap();
    EWAHIterator i = a.getEWAHIterator();
    EWAHIterator j = getEWAHIterator();
    if(!(i.hasNext() && j.hasNext())) {// hopefully this never happens...
      container.setSizeInBits(sizeInBits());
      return container;
    }
    // at this point, this should be safe:
    BufferedRunningLengthWord rlwi = new BufferedRunningLengthWord(i.next());
    BufferedRunningLengthWord rlwj = new BufferedRunningLengthWord( j.next());
    //RunningLength;
    while (true) {
      boolean i_is_prey = rlwi.size()<rlwj.size();
      BufferedRunningLengthWord prey = i_is_prey ? rlwi: rlwj;
      BufferedRunningLengthWord predator = i_is_prey ? rlwj: rlwi;
      if(!prey.getRunningBit()) {
        long predatorrl = predator.getRunningLength();
        long preyrl = prey.getRunningLength();
        long  tobediscarded = (predatorrl >= preyrl) ?  preyrl : predatorrl;
        container.addStreamOfEmptyWords(predator.getRunningBit(), tobediscarded);
        long dw_predator = i_is_prey ? j.dirtyWords(): i.dirtyWords();
        container.addStreamOfDirtyWords(i_is_prey ? j.buffer(): i.buffer(), dw_predator, preyrl - tobediscarded);
        predator.discardFirstWords(preyrl);
        prey.discardFirstWords(preyrl);
      } else {
        // we have a stream of 1x11
        long preyrl  = prey.getRunningLength();
        container.addStreamOfEmptyWords(true, preyrl);
        predator.discardFirstWords(preyrl);
        prey.discardFirstWords(preyrl);
      }
      long predatorrl = predator.getRunningLength();
      if(predatorrl>0){
        if(!predator.getRunningBit()) {
          long nbre_dirty_prey = prey.getNumberOfLiteralWords();
          long tobediscarded = (predatorrl >= nbre_dirty_prey) ? nbre_dirty_prey : predatorrl;
          long dw_prey = i_is_prey ? i.dirtyWords(): j.dirtyWords();
          predator.discardFirstWords(tobediscarded);
          prey.discardFirstWords(tobediscarded);
          container.addStreamOfDirtyWords(i_is_prey ? i.buffer(): j.buffer(),dw_prey, tobediscarded);
        } else {
          long nbre_dirty_prey = prey.getNumberOfLiteralWords();
          int dw_prey = i_is_prey ? i.dirtyWords(): j.dirtyWords();
          long tobediscarded = (predatorrl >= nbre_dirty_prey) ? nbre_dirty_prey : predatorrl;
          //nbre_dirty_prey -= tobediscarded;
          container.addStreamOfEmptyWords(true, tobediscarded);
          //dw_prey += tobediscarded;
          predator.discardFirstWords(tobediscarded);
          prey.discardFirstWords(tobediscarded);
        }
      }
      // all that is left to do now is to OR the dirty words
      long nbre_dirty_prey = prey.getNumberOfLiteralWords();
      if(nbre_dirty_prey > 0) {
        for(int k = 0; k< nbre_dirty_prey;++k) {
          container.add(i.buffer()[i.dirtyWords()+k] | j.buffer()[i.dirtyWords()+k]);
        }
        predator.discardFirstWords(nbre_dirty_prey);
      }
      if( i_is_prey ) {
        if(!i.hasNext()) break;
        rlwi = new BufferedRunningLengthWord(i.next());
      } else {
        if(!j.hasNext()) break;
        rlwj = new BufferedRunningLengthWord( j.next());
      }
    }
    container.setSizeInBits(sizeInBits());
    return container;
  }

  /*
     * set the bit at position i to true, the bits must
     * be set in increasing order.
     */
  public void set(int i) {
    assert i>= sizeinbits;
    // must I complete a word?
    if ( (sizeinbits % 64) != 0) {
      int possiblesizeinbits = (sizeinbits /64)*64 + 64;
      if(possiblesizeinbits<i+1) {
        sizeinbits = possiblesizeinbits;
      }
    }
    addStreamOfEmptyWords(false, (i/64) - sizeinbits/64);
    int bittoflip = i-(sizeinbits/64 * 64);
    // next, we set the bit
    if(( rlw.getNumberOfLiteralWords() == 0) || ((sizeinbits -1)/64 < i/64) ){
      long newdata = 1l<<bittoflip;
      addLiteralWord(newdata);
    } else {
      buffer[actualsizeinwords-1] |= 1l<<bittoflip;
      // check if we just completed a stream of 1s
      if(buffer[actualsizeinwords-1] == ~0l)  {
        // we remove the last dirty word
        buffer[actualsizeinwords-1] = 0;
        --actualsizeinwords;
        rlw.setNumberOfLiteralWords(rlw.getNumberOfLiteralWords()-1);
        // next we add one clean word
        addEmptyWord(true);
      }
    }
    sizeinbits = i+1;
  }

  /**
   * This is normally how you add data to the array. So you add
   * bits in streams of 8*8 bits.
   *
   * @param newdata newdata
   * @return the number of words added to the buffer
   */
  public int add(long newdata) {
    return add(newdata,wordinbits);
  }

  /**
   * Suppose you want to add a bunch of zeroes or ones?
   * This is the method you use.
   *
   *
   * @param v v
   * @param number number
   * @return the number of words added to the buffer
   */
  public int addStreamOfEmptyWords(boolean v, long number) {
    if(number == 0) return 0;
    boolean noliteralword = (rlw.getNumberOfLiteralWords() == 0);
    long runlen = rlw.getRunningLength();
    if( ( noliteralword ) && ( runlen == 0 )) {
      rlw.setRunningBit(v);
    }
    int wordsadded = 0;
    if( ( noliteralword ) && (rlw.getRunningBit() == v)
        && (runlen < RunningLengthWord.largestrunninglengthcount) ) {
      long whatwecanadd = Math.min(number, RunningLengthWord.largestrunninglengthcount -runlen);
      rlw.setRunningLength(runlen+whatwecanadd);
      sizeinbits += whatwecanadd*wordinbits;
      if(number - whatwecanadd> 0 ) wordsadded += addStreamOfEmptyWords(v, number - whatwecanadd);
    } else {
      push_back(0);
      ++wordsadded;
      rlw.position = actualsizeinwords - 1;
      long whatwecanadd = Math.min(number, RunningLengthWord.largestrunninglengthcount);
      rlw.setRunningBit(v);
      rlw.setRunningLength(whatwecanadd);
      sizeinbits += whatwecanadd*wordinbits;
      if(number - whatwecanadd> 0 ) wordsadded += addStreamOfEmptyWords(v, number - whatwecanadd);
    }
    return wordsadded;
  }

  /**
   * if you have several words to copy over, this might be faster.
   *
   * @param data data
   * @param start start
   * @param number number
   * @return ?
   */
  long addStreamOfDirtyWords(long[] data, long start, long number) {
    if(number == 0) return 0;
    long NumberOfLiteralWords = rlw.getNumberOfLiteralWords();
    long whatwecanadd = Math.min(number, RunningLengthWord.largestliteralcount - NumberOfLiteralWords);
    rlw.setNumberOfLiteralWords(NumberOfLiteralWords+whatwecanadd);
    long leftovernumber = number -whatwecanadd;
//    long oldsize = actualsizeinwords;
    push_back(data,(int)start,(int)whatwecanadd);
    long wordsadded = whatwecanadd;
    if(leftovernumber>0) {
      push_back(0);
      rlw.position=actualsizeinwords - 1;
      ++wordsadded;
      wordsadded+=addStreamOfDirtyWords(data,start+whatwecanadd, leftovernumber);
    }
    return wordsadded;
  }

  /*
     * sometimes, at the end, you don't have 8*8 bits to add,
     * so use this method instead.
     &
     * @returns the number of words added to the buffer
     */
  public int add(long  newdata, int bitsthatmatter) {
    sizeinbits += bitsthatmatter;
    if(newdata == 0) {
      return addEmptyWord(false);
    } else if (newdata == ~0l) {
      return addEmptyWord(true);
    } else {
      return addLiteralWord(newdata);
    }
  }

  public int sizeInBits() {
    return sizeinbits;
  }
  public void setSizeInBits(int size) {
    sizeinbits = size;
  }
  public int sizeInBytes() {
    return actualsizeinwords*8;
  }
  private void push_back(long data) {
    if(actualsizeinwords==buffer.length) {
      long oldbuffer[] = buffer;
      buffer = new long[oldbuffer.length * 2];
      System.arraycopy(oldbuffer,0,buffer,0,oldbuffer.length);
    }
    buffer[actualsizeinwords++] = data;
  }

  private void push_back(long[] data,int start, int number) {
    while(actualsizeinwords + number >=buffer.length) {
      long oldbuffer[] = buffer;
      buffer = new long[oldbuffer.length * 2];
      System.arraycopy(oldbuffer,0,buffer,0,oldbuffer.length);
    }
    System.arraycopy(data,start,buffer,actualsizeinwords,number);
    actualsizeinwords+=number;
  }

  private int addEmptyWord(boolean v) {
    boolean noliteralword = (rlw.getNumberOfLiteralWords() == 0);
    long runlen = rlw.getRunningLength();
    if( ( noliteralword ) && ( runlen == 0 )) {
      rlw.setRunningBit(v);
    }
    if( ( noliteralword ) && (rlw.getRunningBit() == v)
        && (runlen < RunningLengthWord.largestrunninglengthcount) ) {
      rlw.setRunningLength(runlen+1);
      return 0;
    } else {
      push_back(0);
      rlw.position = actualsizeinwords - 1;
      rlw.setRunningBit(v);
      rlw.setRunningLength(1);
      return 1;
    }
  }

  private int addLiteralWord(long  newdata) {
    long numbersofar = rlw.getNumberOfLiteralWords();
    if(numbersofar >= RunningLengthWord.largestliteralcount) {
      push_back(0);
      rlw.position = actualsizeinwords - 1;
      rlw.setNumberOfLiteralWords(1);
      push_back(newdata);
      return 2;
    }
    rlw.setNumberOfLiteralWords(numbersofar + 1);
    push_back(newdata);
    return 1;
  }



  /**
   * reports the number of bits set
   * @return cardinality
   */
  public int cardinality() {
    int counter = 0;
    EWAHIterator i = new EWAHIterator(buffer,actualsizeinwords);
    while(i.hasNext()) {
      RunningLengthWord rlw = i.next();
      if(rlw.getRunningBit()) {
        counter += wordinbits*rlw.getRunningLength();
      } else {
      }
      for(int j = 0; j<rlw.getNumberOfLiteralWords();++j) {
        long data = i.buffer()[i.dirtyWords()+j];
        for(int c= 0; c<wordinbits; ++c)
          if((data & (1l<<c)) != 0) ++counter;
      }
    }
    return counter;
  }

  public String toString() {
    // TODO stop using string concatination
    String ans = " EWAHCompressedBitmap, size in words = " + actualsizeinwords + "\n";
    EWAHIterator i = new EWAHIterator(buffer,actualsizeinwords);
    while (i.hasNext()) {
      RunningLengthWord rlw = i.next();
      if (rlw.getRunningBit()) {
        ans+=rlw.getRunningLength()+" 1x11\n";
      } else {
        ans+=rlw.getRunningLength()+" 0x00\n";
      }
      ans+=rlw.getNumberOfLiteralWords()+" dirties\n";
      //for(int j = 0; j<rlw.getNumberOfLiteralWords();++j) {
      //	long data = i.buffer()[i.dirtyWords()+j];
      //	ans+="\t"+data+"\n";
      //}
    }
    return ans;
  }

  /**
   * iterate over the positions of the true values.
   * @return iterator
   */
  public Iterator<Integer> iterator() {
    final EWAHIterator i = new EWAHIterator(buffer, actualsizeinwords);
    return new Iterator<Integer>() {
      int pos = 0;
      RunningLengthWord rlw = null;
      Vector<Integer> buffer = new Vector<Integer>();
      int bufferpos= 0;
      public boolean 	hasNext() {
        if(rlw == null)
          if(!loadNextRLE())
            return false;
          else {
            loadBuffer();
            return true;
          }
        else
          return true;
      }

      private boolean loadNextRLE() {
        while(i.hasNext()) {
          rlw = i.next();
          if(rlw.getRunningBit() && (rlw.getRunningLength()>0) )
            return true;
          if(rlw.getNumberOfLiteralWords()>0)
            return true;
        }
        return false;

      }

      private void loadBuffer() {
        buffer = new Vector<Integer>();
        bufferpos = 0;
        if(rlw.getRunningBit()) {
          for(int j = 0; j<rlw.getRunningLength();++j) {
            for(int c= 0; c<wordinbits;++c)
              buffer.add(pos++);
          }
        } else {
          pos+=wordinbits*rlw.getRunningLength();
        }
        for(int j = 0; j<rlw.getNumberOfLiteralWords();++j) {
          long data = i.buffer()[i.dirtyWords()+j];
          for(long c= 0; c<wordinbits;++c) {
            if( ((1l << c) & data) != 0) {
              buffer.add(pos);
            }
            ++pos;
          }
        }

      }
      public Integer 	next() {
        if(buffer.size() == bufferpos+1)
          rlw = null;
        return buffer.get(bufferpos++);
      }
      public void remove() {
        throw new RuntimeException("not implemented");
      }
    };
  }

  /**
   * get the locations of the true values as one vector.
   * (may use more memory than iterator())
   * @return positionts
   */
  public Vector<Integer> getPositions() {
    Vector<Integer> v = new Vector<Integer>();
    EWAHIterator i = new EWAHIterator(buffer,actualsizeinwords);
    int pos = 0;
    while(i.hasNext()) {
      RunningLengthWord rlw = i.next();
      if(rlw.getRunningBit()) {
        for(int j = 0; j<rlw.getRunningLength();++j) {
          for(int c= 0; c<wordinbits;++c)
            v.add(pos++);
        }
      } else {
        pos+=wordinbits*rlw.getRunningLength();
      }
      for(int j = 0; j<rlw.getNumberOfLiteralWords();++j) {
        long data = i.buffer()[i.dirtyWords()+j];
        for(long c= 0; c<wordinbits;++c) {
          if( ((1l << c) & data) != 0) {
            v.add(pos);
          }
          ++pos;
        }
      }
    }
    while( (v.size()>0) && (v.lastElement() >= sizeinbits ))
      v.removeElementAt(v.size()-1);
    return v;
  }


  public Object clone() throws java.lang.CloneNotSupportedException {
    EWAHCompressedBitmap clone = (EWAHCompressedBitmap) super.clone();
    clone.buffer = this.buffer.clone();
    clone.actualsizeinwords = this.actualsizeinwords;
    clone.sizeinbits = this.sizeinbits;
    return clone;
  }

  public void	readExternal(ObjectInput in) throws IOException {
    sizeinbits = in.readInt();
    actualsizeinwords = in.readInt();
    buffer = new long[in.readInt()];
    for(int k = 0; k< actualsizeinwords; ++k)
      buffer[k] = in.readLong();
    rlw = new RunningLengthWord(buffer,actualsizeinwords-1);
  }

  public void	writeExternal(ObjectOutput out) throws IOException  {
    out.writeInt(sizeinbits);
    out.writeInt(actualsizeinwords);
    out.writeInt(buffer.length);
    for(int k = 0; k< actualsizeinwords; ++k)
      out.writeLong(buffer[k]);
  }


  static final int defaultbuffersize = 512;
  long buffer[] = new long[defaultbuffersize];
  int actualsizeinwords = 1;
  int sizeinbits = 0;
  RunningLengthWord rlw = new RunningLengthWord(buffer,0);
  public static final int wordinbits = 8*8;


  static class EWAHIterator {
    RunningLengthWord rlw;
    int size;
    int pointer;

    public EWAHIterator(long[] a, int sizeinwords) {
      rlw = new RunningLengthWord(a,0);
      size = sizeinwords;
      pointer = 0;
    }

    boolean hasNext() {
      return pointer<size;
    }

    RunningLengthWord next() {
      rlw.position = pointer;
      pointer += rlw.getNumberOfLiteralWords() + 1;
      return rlw;
    }

    int dirtyWords()  {
      return pointer-(int)rlw.getNumberOfLiteralWords();
    }

    long[] buffer() {
      return rlw.array;
    }

  }
}