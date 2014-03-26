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
 *    CommonWordBlocker.java
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
 * @author Beena Kamath
 */

public class CommonWordBlocker extends Blocker
  implements OptionHandler, Serializable {


  /** Delimiters for tokenizing */
  protected final String delimiters =
    " \t\n\r\f\'\"\\!@#$%^&*()_-+={}<>,.;:|[]{}/*~`";
 
  /** Initializations 
   */
  public CommonWordBlocker() {
  }

  /** Return a copy of this Blocker */
  public Blocker clone() {
    CommonWordBlocker blocker = new CommonWordBlocker();
    return blocker;
  }

  /** Blocks are overlapping (each instance belongs to multiple blocks) */
  public boolean blocksOverlap() {
    return true;
  }


  /** Return the list of blocks for a given instance */
  public Object[] getBlocks (Instance instance) {
    String attrValue = instance.stringValue(m_attrId);
      
    StringTokenizer tokenizer = new StringTokenizer(attrValue, delimiters);
    HashSet<String> blocks = new HashSet<String>();
    while (tokenizer.hasMoreTokens()) {
      String blockValue = tokenizer.nextToken();
      blocks.add(blockValue); 
    }

    return blocks.toArray(); 
  }


  /** Given two instances, do they fall into the same block? */
  public boolean sameBlock(Instance instance1, Instance instance2) {
    String str1 = instance1.stringValue(m_attrId);
    String str2 = instance2.stringValue(m_attrId);

    HashSet<String> set1 = new HashSet<String>(); 
    StringTokenizer tokenizer = new StringTokenizer(str1, delimiters);
    while (tokenizer.hasMoreTokens()) {
      set1.add(tokenizer.nextToken());
    }

    tokenizer = new StringTokenizer(str2, delimiters);
    while (tokenizer.hasMoreTokens()) {
      if (set1.contains(tokenizer.nextToken())) {
        return true;
      }
    }

    return false; 
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

  public int type() {
    return Attribute.STRING;
  }

}
