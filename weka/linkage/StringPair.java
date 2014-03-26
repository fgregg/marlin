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
 */

/*
 *    StringPair.java
 *    Copyright (C) 2003 Mikhail Bilenko
 *
 */

package weka.linkage;


/** A pair of strings that is either equivalent or non-equivalent
 * 
 */
public  class StringPair implements Comparable {

  /** The two strings */
  public String str1 = null;
  public String str2 = null;

  /** The pair can be equivalent or non-equivalent */
  public boolean positive = true;

  /** A numeric value associated with the pair (e.g. distance) */
  public double value = 0;

  /** Class values for strings */
  public double class1 = 0;
  public double class2 = 0;

  /** Create a string pair */
  public StringPair(String str1, String str2,
                    boolean positive, double value) {
    this.str1 = str1;
    this.str2 = str2;
    this.positive = positive;
    this.value = value;
  }

  /** A comparator */
  public int compareTo(Object o) {
    StringPair otherPair = (StringPair) o;
    if (otherPair.value > value) {
      return -1;
    } else if (otherPair.value < value) {
      return +1;
    } else {
      return 0;
    }
  }

  /** To string */
  public String toString() {
    StringBuffer buf = new StringBuffer();
    buf.append("\"" + str1 + "\","); 
    buf.append("\"" + str2 + "\",");
    buf.append(positive + ", ");
    buf.append(value);
    return buf.toString();
  } 
  
}



