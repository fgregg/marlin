/*
 *    This program is free software; you can rediinstanceibute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is diinstanceibuted in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    InstancePairComparator.java
 *    Copyright (C) 2003 Mikhail Bilenko
 *
 */

package weka.linkage.blocking;

import java.io.Serializable;

import weka.linkage.*;
import weka.core.Instance;

/** This is a basic class for comparing a training pair
 *  Moved from Blocking 2005 Beena Kamath
 * 
 */
public class InstancePairComparator implements java.util.Comparator, Serializable { 
  public InstancePairComparator() {}
  public int compare(Object o1, Object o2) {
    // InstancePairs implement Comparable!
    int result = ((Comparable)o1).compareTo(o2);
    if (result != 0) {
      return -result;
    } else {  
// ties are resolved in a very ad hoc way:  comparing values of attributes of the first pair... TODO: a better way?
      InstancePair p1 = (InstancePair) o1;
      for (int i = 0; i < p1.instance1.numValues(); i++) {
        double v1 = p1.instance1.value(i);
        double v2 = p1.instance2.value(i);
        if (v1 != v2) {
          return ((v1-v2) > 0) ? 1 : -1;
        }
      }

      InstancePair p2 = (InstancePair) o2;
      for (int i = 0; i < p2.instance1.numValues(); i++) {
        double v1 = p2.instance1.value(i);
        double v2 = p2.instance2.value(i);
        if (v1 != v2) {
          return ((v1-v2) > 0) ? 1 : -1;
        }
      }

      for (int i = 0; i < p1.instance1.numValues(); i++) {
        double v1 = p1.instance1.value(i);
        double v2 = p2.instance1.value(i);
        if (v1 != v2) {
          return ((v1-v2) > 0) ? 1 : -1;
        }
      }

//       System.err.println("WTF:" + p1.value + "=" + p2.value); 
//       System.out.println("\t" + p1.instance1);
//       System.out.println("\t" + p1.instance2);

//       System.out.println("\t\t" + p2.instance1);
//       System.out.println("\t\t" + p2.instance2);
        
      String s1 = p1.instance1.stringValue(0);
      if (s1.length() < 2) return 1;
      return (s1.charAt(0) > s1.charAt(1)) ? 1 : -1;
    }
  }
}

