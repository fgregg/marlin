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
 *    FirstThreeCharBlocker.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking.learn;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import weka.linkage.*;

/**
 * This class takes a set of records, creates blocks based on the
 * criterion of having the same first three characters. 
 *
 * @author Beena Kamath, modified by Misha Bilenko 
 */

public class FirstNCharBlocker extends Blocker
  implements OptionHandler, Serializable {

  /** The number of chars that should match */
  protected int m_N = 3; 

  /** Initializations */
  public FirstNCharBlocker() {
  }

  /** Return a copy of this Blocker */
  public Blocker clone() {
    FirstNCharBlocker blocker = new FirstNCharBlocker();
    blocker.setN(m_N); 
    return blocker;
  }

  /** Blocks are disjoint (each instance belongs to only one block) */
  public boolean blocksOverlap() {
    return false;
  }


  /** Return the list of blocks for a given instance */
  public Object[] getBlocks (Instance instance) {
    String blockValue = instance.stringValue(m_attrId);
    if (blockValue.length() >= m_N) {
      blockValue = blockValue.substring(0, m_N);
    }

    Object[] blocks = new Object[1];
    blocks[0] = blockValue;
    
    return blocks; 
  }

  
  /** Given two instances, do they fall into the same block? */
  public boolean sameBlock(Instance instance1, Instance instance2) {
    String str1 = instance1.stringValue(m_attrId);
    String str2 = instance2.stringValue(m_attrId);

    String blockVal1 = (str1.length() < m_N ? str1 : str1.substring(0, m_N));
    String blockVal2 = (str2.length() < m_N ? str2 : str2.substring(0, m_N));

    return (blockVal1.equals(blockVal2));
  }
  
  
  /** Set/get the number of chars that must match */
  public void setN(int N) { m_N = N; }
  public int getN() { return m_N; }


  /**
   * Gets the current settings of the blocker.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [1];
    options[0] = "-N_" + m_N; 
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

  public int type() {
    return Attribute.STRING;
  }

   /** toString */
  public String toString() {
    return Utils.removeSubstring(this.getClass().getName(),
                                 "weka.linkage.blocking.learn.")
      + "-" +
      (m_instances == null ? m_attrId : m_instances.attribute(m_attrId).name())
      + "-" + m_N;
  } 

}
