/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *    FastPair.java
 *    Copyright (C) 2006 Misha Bilenko
 *
 */

package weka.linkage.blocking.learn;

import java.util.Arrays;
import java.util.Comparator;


/** A small class for storing pairs as indeces with
 * some numeric value
 * @author Misha Bilenko
 */

public class FastPair implements Comparable<FastPair> {

  /** index and value are public */
  public int idx;
  public double value;

  /** A basic constructor */
  public FastPair(int idx, double value) {
    this.idx = idx;
    this.value = value;
  }

  /** Equals is based on the index */
  public boolean equals(Object o) {
    if (o instanceof FastPair) {
      return idx == ((FastPair)o).idx;
    } 
    return false; 
  }


  /** CompareTo is based on the index */
  public int compareTo(FastPair anotherPair) {
    int thisIdx = this.idx;
    int anotherIdx = anotherPair.idx;
    return (thisIdx<anotherIdx ? -1 : (thisIdx==anotherIdx ? 0 : 1));
  }


  /** Hash code is based on the index */
  public int hashCode() {
    return idx;
  }

//   /** A value-based comparator for fast pairs
//       Copy-paste as an anonymous class! */
//   public class ValueComparator implements Comparator<FastPair> {
//     public int compare(FastPair pair1, FastPair pair2) {
//       double value1 = pair1.idx;
//       double value2 = pair2.idx;
//       return (value1<value2 ? -1 : (value1==value2 ? 0 : 1));
//     }
//   }

  public String toString() {
    return "{" + idx + ", " + value + "}";
  }

  public static void main(String[] args) {
    FastPair[] pairs = { new FastPair(2, 0.1),
                         new FastPair(5, 0.3),
                         new FastPair(-1, 44),
                         new FastPair(24, 0.245),
                         new FastPair(12, 0.1),
                         new FastPair(222, 0.22),
                         new FastPair(21, 0.15)};

    for (FastPair pair : pairs) 
      System.out.println(pair);

    System.out.println("\n\n"); 
    Arrays.sort(pairs); 
    for (FastPair pair : pairs) 
      System.out.println(pair); 

    Comparator<FastPair> valueComparator = new Comparator<FastPair>() {
      public int compare(FastPair pair1, FastPair pair2) {
        double value1 = pair1.value;
        double value2 = pair2.value;
        return (value1<value2 ? -1 : (value1==value2 ? 0 : 1));
      }
    };
    System.out.println("\n\n"); 
    Arrays.sort(pairs, valueComparator); 
    for (FastPair pair : pairs) 
      System.out.println(pair);

    int idx = Arrays.binarySearch(pairs, new FastPair(4, 0.2),
                                  valueComparator); 
    System.out.println(idx);
  } 
}
