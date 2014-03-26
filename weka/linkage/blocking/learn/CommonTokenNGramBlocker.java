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
 *    CommonTokenNGramBlocker.java
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

public class CommonTokenNGramBlocker extends Blocker
  implements OptionHandler, Serializable {

  /** number of consecutive tokens to use */
  protected int m_N = 2; 
  

  /** Delimiters for tokenizing */
  protected final String delimiters =
    " \t\n\r\f\'\"\\!@#$%^&*()_-+={}<>,.;:|[]{}/*~`";
 
  /** Initializations 
   */
  public CommonTokenNGramBlocker() {
  }

  /** Return a copy of this Blocker */
  public Blocker clone() {
    CommonTokenNGramBlocker blocker = new CommonTokenNGramBlocker();
    blocker.setN(m_N);
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
    List<String> tokenList = new ArrayList<String>();
    while (tokenizer.hasMoreTokens()) {
      tokenList.add(tokenizer.nextToken()); 
    }
      
    HashSet<String> blocks = new HashSet<String>();
    for (int i = 0; i < tokenList.size() - m_N+1; i++) {
      StringBuffer buff = new StringBuffer(tokenList.get(i));
      for (int j = i+1; j < i+m_N; j++) {
        buff.append(tokenList.get(j));
      }
      String blockValue = buff.toString();
      blocks.add(blockValue); 
    }
    return blocks.toArray(); 
  } 


  /** Given two instances, do they fall into the same block? */
  public boolean sameBlock(Instance instance1, Instance instance2) {
    String str1 = instance1.stringValue(m_attrId);
    String str2 = instance2.stringValue(m_attrId);

    HashSet<String> set1 = stringToTokenGrams(str1);
    HashSet<String> set2 = stringToTokenGrams(str2); 

    for (String tokGram1 : set1) {
      if (set2.contains(tokGram1)) { 
        return true;
      }
    }
    return false; 
  }


  /** Given a string, generate a set of token n-grams for it */ 
  protected final HashSet<String> stringToTokenGrams(String str) {
    HashSet<String> strSet = new HashSet<String>(); 
    
    StringTokenizer tokenizer = new StringTokenizer(str, delimiters);
    List<String> tokenList = new ArrayList<String>();
    while (tokenizer.hasMoreTokens()) {
      tokenList.add(tokenizer.nextToken()); 
    }

    for (int i = 0; i < tokenList.size() - m_N+1; i++) {
      StringBuffer buff = new StringBuffer(tokenList.get(i));
      for (int j = i+1; j < i+m_N; j++) {
        buff.append(tokenList.get(j));
      }
      strSet.add(buff.toString()); 
    }
    return strSet; 
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
    if (m_N > 1) { 
      options[0] = "-N_" + m_N;
    } else {
      options[0] = "";
    }

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
