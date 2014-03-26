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
 *    RandomBlockingLearner.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking.learn;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import weka.linkage.*;

/**
 * This class takes a set of blockers and returns 
 * a random list of blockers.
 *
 * @author Beena Kamath
 */

public class RandomBlockingLearner extends BlockingLearner implements OptionHandler, Serializable {

  /** Initialize the blocking learner.
   */
  public RandomBlockingLearner() {
  }

   /** Given a set of records and the feature to be blocked,
   *   build the vector space
   */
  public ArrayList getBlockers(Blocker[] blockers, Instances data) {

    ArrayList allBlockers = new ArrayList();
    int classIdx = data.classIndex();
    for (int i = 0; i < data.numAttributes(); i++) {
      if (i != classIdx) {
        for (int j = 0; j < blockers.length; j++) {
          if (((blockers[j].type()  == Attribute.NUMERIC) ||
               (data.attribute(i).type() == Attribute.NUMERIC)) &&
              (blockers[j].type() != data.attribute(i).type())) {
            continue;
          }
          Blocker blocker = (Blocker)blockers[j].clone();
          blocker.setAttribute(i); 
          allBlockers.add(blocker);
        }
      }
    }
    Collections.shuffle(allBlockers);

    return allBlockers; 
  }


  /**
   * Gets the current settings of the blocker.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [0];

    return options;
  }

  /**
   * Parses a given list of options. Valid options are:<p>
   *
   */
  public void setOptions(String[] options) throws Exception {
  }

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {
    Vector newVector = new Vector(0);

    return newVector.elements();
  }

}
