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
 *    Blocking.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking;

import weka.core.Instances;
import weka.core.Utils;
import java.util.*;

import weka.linkage.*;

/**
 * An abstract class for blocking duplicate records
 *
 * @author Beena Kamath
 */
public abstract class Blocking implements Cloneable {

  /** An arraylist of Object arrays containing statistics */           
  protected ArrayList m_statistics = null;

  /** Blocking may be forced to include *all* positives */
  protected boolean m_includeAllDuplicates = false;
  public void setIncludeAllDuplicates(boolean b) {
    m_includeAllDuplicates = b;
  }
  

  /** Learn the blocking criteria */
  public abstract void learnBlocking(Instances data);


  /** Given a list of strings, build the vector space
  */
  public abstract void buildIndex(Instances instances) throws Exception;


  /** Return n most similar pairs
   */
  public abstract InstancePair[] getMostSimilarPairs(int numPairs);


  public static Blocking forName(String blockingName, String[] options) throws Exception {
    return (Blocking)Utils.forName(Blocking.class,
                                  blockingName,
                                  options);
  }

  /** Return the list of statistics collected during blocking
   * @returns collected statistics
   */
  public ArrayList getStatistics() {
    return m_statistics;
  }


} 


















