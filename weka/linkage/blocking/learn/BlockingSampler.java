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
 *    BlockingSampler.java
 *    Copyright (C) 2006 Misha Bilenko
 *
 */


package weka.linkage.blocking.learn;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import java.text.SimpleDateFormat;

import weka.linkage.*;
import weka.linkage.metrics.*;
import weka.linkage.blocking.*;

/**
 * An abstract class for taking a set of instances
 * and creating a random subsample. Children will
 * determine the sampling specifics. 
 *
 * @author Misha Bilenko
 */
public abstract class BlockingSampler
  implements Serializable {
  
  /** Upper bound on the number of instances in the sample.  */
  protected int m_maxNumInstances = Integer.MAX_VALUE; 

  /** Given a set of instances, randomly create a subsample */
  public abstract Instances getSample(Instances data);

  /** Set/get the max number of instances to be sampled */
  public int getMaxNumInstances() { return m_maxNumInstances; }
  public void setMaxNumInstances(int max) { m_maxNumInstances = max; }

  /** A helper function that takes instances and creates a map
   * where each class is mapped to an array of instance id's */
  public static HashMap<Double, int[]> createClassInstanceMap(Instances data) {

    HashMap<Double, int[]> map = new HashMap<Double, int[]>(data.numClasses());

    int numInstances = data.numInstances();
    for (int i = 0; i < numInstances; i++) {
      double classID = data.instance(i).classValue();
      int[] classIndeces = map.get(classID);
      if (classIndeces == null) { // haven't seen this class before
        classIndeces = new int[1];
        map.put(classID, classIndeces);
      } else { // have seen the class, need to resize
        int[] newIndeces = new int[classIndeces.length+1];
        System.arraycopy(classIndeces, 0, newIndeces, 0, classIndeces.length);
        newIndeces[classIndeces.length] = i;
        classIndeces = null;
        map.put(classID, newIndeces);
      }
    }

    return map; 
  } 

  
  /** Gets the options
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [2];
    int current = 0;

    if (m_maxNumInstances < Integer.MAX_VALUE) {
      options[current++] = "-maxInst";
      options[current++] = "" + m_maxNumInstances;
    }
    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

} 


















