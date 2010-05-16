package com.googlecode.JavaEWAH;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

public class TestEWAH extends TestCase {

  public void testRunningLengthWord() {
    long x[] = new long[1];
    RunningLengthWord rlw = new RunningLengthWord(x,0);
    equal(0,rlw.getNumberOfLiteralWords());
    equal(false,rlw.getRunningBit());
    equal(0,rlw.getRunningLength());
    rlw.setRunningBit(true);
    equal(0,rlw.getNumberOfLiteralWords());
    equal(true,rlw.getRunningBit());
    equal(0,rlw.getRunningLength());
    rlw.setRunningBit(false);
    equal(0,rlw.getNumberOfLiteralWords());
    equal(false,rlw.getRunningBit());
    equal(0,rlw.getRunningLength());
    for(long rl = rlw.largestliteralcount; rl >=0; rl-=1024) {
      rlw.setNumberOfLiteralWords(rl);
      equal(rl,rlw.getNumberOfLiteralWords());
      equal(false,rlw.getRunningBit());
      equal(0,rlw.getRunningLength());
      rlw.setNumberOfLiteralWords(0);
      equal(0,rlw.getNumberOfLiteralWords());
      equal(false,rlw.getRunningBit());
      equal(0,rlw.getRunningLength());
    }
    for(long rl = 0; rl <=rlw.largestrunninglengthcount; rl+=1024) {
      rlw.setRunningLength(rl);
      equal(0,rlw.getNumberOfLiteralWords());
      equal(false,rlw.getRunningBit());
      equal(rl,rlw.getRunningLength());
      rlw.setRunningLength(0);
      equal(0,rlw.getNumberOfLiteralWords());
      equal(false,rlw.getRunningBit());
      equal(0,rlw.getRunningLength());
    }
    rlw.setRunningBit(true);
    for(long rl = 0; rl <=rlw.largestrunninglengthcount; rl+=1024) {
      rlw.setRunningLength(rl);
      equal(0,rlw.getNumberOfLiteralWords());
      equal(true,rlw.getRunningBit());
      equal(rl,rlw.getRunningLength());
      rlw.setRunningLength(0);
      equal(0,rlw.getNumberOfLiteralWords());
      equal(true,rlw.getRunningBit());
      equal(0,rlw.getRunningLength());
    }
    for(long rl = 0; rl <=rlw.largestliteralcount; rl+=128) {
      rlw.setNumberOfLiteralWords(rl);
      equal(rl,rlw.getNumberOfLiteralWords());
      equal(true,rlw.getRunningBit());
      equal(0,rlw.getRunningLength());
      rlw.setNumberOfLiteralWords(0);
      equal(0,rlw.getNumberOfLiteralWords());
      equal(true,rlw.getRunningBit());
      equal(0,rlw.getRunningLength());
    }
  }


  public void testEWAHCompressedBitmap() {
    long zero = 0;
    long specialval = 1l | (1l << 4)|(1l << 63);
    long notzero = ~zero;
    EWAHCompressedBitmap myarray1 = new EWAHCompressedBitmap();
    myarray1.add(zero);
    myarray1.add(zero);
    myarray1.add(zero);
    myarray1.add(specialval);
    myarray1.add(specialval);
    myarray1.add(notzero);
    myarray1.add(zero);
    equal(myarray1.getPositions().size(), 6+64);
    EWAHCompressedBitmap myarray2 = new EWAHCompressedBitmap();
    myarray2.add(zero);
    myarray2.add(specialval);
    myarray2.add(specialval);
    myarray2.add(notzero);
    myarray2.add(zero);
    myarray2.add(zero);
    myarray2.add(zero);
    equal(myarray2.getPositions().size(), 6+64);
    Vector<Integer> data1 = myarray1.getPositions();
    //System.out.println("data1");
    //for(Integer i : data1) System.out.println(i);
    Vector<Integer> data2 = myarray2.getPositions();
    //System.out.println("data2");
    //for(Integer i : data2) System.out.println(i);
    Vector<Integer> logicalor = new Vector<Integer>();
    {HashSet<Integer> tmp = new HashSet<Integer>();
      tmp.addAll(data1);
      tmp.addAll(data2);
      logicalor.addAll(tmp);}
    Collections.sort(logicalor);
    Vector<Integer> logicaland = new Vector<Integer>();
    logicaland.addAll(data1);
    logicaland.retainAll(data2);
    Collections.sort(logicaland);
    EWAHCompressedBitmap arrayand = myarray1.and(myarray2);
    isTrue(arrayand.getPositions().equals(logicaland));
    EWAHCompressedBitmap arrayor = myarray1.or(myarray2);
    isTrue(arrayor.getPositions().equals(logicalor));
    EWAHCompressedBitmap arrayandbis = myarray2.and(myarray1);
    isTrue(arrayandbis.getPositions().equals(logicaland));
    EWAHCompressedBitmap arrayorbis = myarray2.or(myarray1);
    isTrue(arrayorbis.getPositions().equals(logicalor));
    EWAHCompressedBitmap x = new EWAHCompressedBitmap();
    for(Integer i: myarray1.getPositions()) {
      x.set(i.intValue());
    }
    isTrue(x.getPositions().equals(myarray1.getPositions()));
    x = new EWAHCompressedBitmap();
    for(Integer i: myarray2.getPositions()) {
      x.set(i.intValue());
    }
    isTrue(x.getPositions().equals(myarray2.getPositions()));
    x = new EWAHCompressedBitmap();
    for(Iterator<Integer> k = myarray1.iterator(); k.hasNext(); ){
      x.set(k.next().intValue());
    }
    isTrue(x.getPositions().equals(myarray1.getPositions()));
    x = new EWAHCompressedBitmap();
    for(Iterator<Integer> k = myarray2.iterator(); k.hasNext(); ){
      x.set(k.next().intValue());
    }
    isTrue(x.getPositions().equals(myarray2.getPositions()));

  }

  /**  as per renaud.delbru, Feb 12, 2009
   * this might throw an error out of bound exception.
   */
  public void testLargeEWAHCompressedBitmap() {
    System.out.println("testing EWAH over a large array");
    EWAHCompressedBitmap myarray1 = new EWAHCompressedBitmap();
    int N= 11000000;
    for(int i = 0; i <N; ++i) {
      myarray1.set(i);
    }
    isTrue(myarray1.sizeInBits() == N);
  }

  public void testCardinality () {
    System.out.println("testing EWAH cardinality");
    EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
    bitmap.set(Integer.MAX_VALUE);
    //System.out.format("Total Items %d\n", bitmap.cardinality());
    isTrue(bitmap.cardinality()==1);

  }
  public void testSetGet () {
    System.out.println("testing EWAH set/get");
    EWAHCompressedBitmap ewcb = new EWAHCompressedBitmap();
    int[] val = {5,4400,44600,55400,1000000};
    for (int k = 0; k< val.length; ++k) {
      ewcb.set(val[k]);
    }
    Vector<Integer> result = ewcb.getPositions();
    isTrue(val.length==result.size());
    for(int k = 0; k<val.length;++k) {
      isTrue(result.get(k)==val[k]);
    }
  }

  public void testExternalization () throws IOException {
    System.out.println("testing EWAH externalization");
    EWAHCompressedBitmap ewcb = new EWAHCompressedBitmap();
    int[] val = {5,4400,44600,55400,1000000};
    for (int k = 0; k< val.length; ++k) {
      ewcb.set(val[k]);
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oo = new ObjectOutputStream(bos);
    ewcb.writeExternal(oo);
    oo.close();
    ewcb = null;
    ewcb = new EWAHCompressedBitmap();
    ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray() );
    ewcb.readExternal(new ObjectInputStream(bis));
    Vector<Integer> result = ewcb.getPositions();
    isTrue(val.length==result.size());
    for(int k = 0; k<val.length;++k) {
      isTrue(result.get(k)==val[k]);
    }
  }
  static void equal(int x, int y) {
    if(x!=y) throw new RuntimeException(x+" != "+y);
  }

  static void equal(long x, long y) {
    if(x!=y) throw new RuntimeException(x+" != "+y);
  }

  static void equal(boolean x, boolean y) {
    if(x!=y) throw new RuntimeException(x+" != "+y);
  }

  static void isTrue(boolean x) {
    if(!x) throw new RuntimeException();
  }
}