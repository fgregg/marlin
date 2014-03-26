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
 *    BlockingSamplerByClass.java
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
public class BlockingSamplerByClass extends BlockingSampler
  implements OptionHandler, Serializable {

  /** Upper bound on the number of true objects. */
  int m_maxNumClasses = Integer.MAX_VALUE;

  /** Proportion of the classes to be sampled */
  double m_fractionOfClasses = 0.1; 

  /** Given a set of instances, randomly create a subsample */
  public Instances getSample(Instances data) {
    Instances sample = new Instances(data, 0);

    // Create a class-instance map
    HashMap<Double, int[]> classInstanceMap = createClassInstanceMap(data);
    Double[] classValues = classInstanceMap.keySet().toArray(new Double[0]);
    int totalClasses = classInstanceMap.size();
    int numClasses = totalClasses; 

    // Depending on the sampling mode, we'll want either a proportion
    // or a certain number of classes
    if (m_fractionOfClasses < 1) {
      numClasses = (int)(totalClasses * m_fractionOfClasses); 
    } else  if (m_maxNumClasses < Integer.MAX_VALUE
                && m_maxNumClasses < totalClasses) {
      numClasses = m_maxNumClasses; 
    } else {
      System.err.println("No need to sample data for learning a blocker");
      return data; 
    }

    // randomly select classes
    int[] sampleClasses = PairwiseSelector.randomSubset(numClasses, totalClasses);

    // start sampling by class id until we either reach
    // the max number of instances, or the max number of classes
    int currClassIdx = 0;
    int numInstances = 0;
    
    while (currClassIdx < sampleClasses.length &&
           numInstances < m_maxNumInstances) {
      // get the instances for the current class ID and add them to the sample
      int classIdx = sampleClasses[currClassIdx];
      double classValue = classValues[classIdx];
      int[] instanceIndeces = classInstanceMap.get(classValue);
      for (int i = 0; i < instanceIndeces.length; i++) {
        sample.add(data.instance(instanceIndeces[i]));
      } 

      currClassIdx++;
    } 

    System.out.println("\n***\nBlockingSamplerByClass: selected " +
                       sample.numInstances() + " instances belonging to " +
                       currClassIdx + " classes from " +
                       data.numInstances() + " original instances in " +
                       totalClasses + " original classes.\n"); 
                       
    return sample; 
  }

  /** Set/get the max number of classes to be sampled */
  public int getMaxNumClasses() { return m_maxNumClasses; }
  public void setMaxNumClasses(int max) { m_maxNumClasses = max; }

  /** Set/get the ratio of classes to be sampled */
  public double getFractionOfClasses() { return m_fractionOfClasses; }
  public void setFractionOfClasses(double f) { m_fractionOfClasses = f; }

  /**
   * Gets the current settings of the blocker.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [10];
    int current = 0; 

    String[] superOptions = super.getOptions();
    for (String option : superOptions) {
      if (option.length() > 0) { 
        options[current++] = option;
      }
    }

    if (m_maxNumClasses < Integer.MAX_VALUE) {
      options[current++] = "-maxCl";
      options[current++] = "" + m_maxNumClasses;
    }

    if (m_fractionOfClasses < 1) {
      options[current++] = "-fracCl";
      options[current++] = "" + m_fractionOfClasses;
    }

     while (current < options.length) {
      options[current++] = "";
    }

    return options;
  }

  /**Parses a given list of options. Valid options are:<p> **/
  public void setOptions(String[] options) throws Exception { }

  /** Returns an enumeration describing the available options.
   * @return an enumeration of all the available options. */
  public Enumeration listOptions() {
    Vector newVector = new Vector(0);
    return newVector.elements();
  }

  
} 

