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
 *    Blocker.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking.learn;

import weka.core.Instances;
import weka.core.Instance;
import weka.core.Utils;
import java.util.*;
import weka.linkage.*;
import java.io.*;

/**
 * An abstract class for a blocking criterion
 *
 * @author Beena Kamath
 */
public abstract class Blocker implements Cloneable {

  /** Stores the blocking map */
  protected BlockingMap m_blockingMap = null;

  /** Instances */
  protected Instances m_instances = null; 

  /** using RedBlue? */
  protected boolean m_redBlue = true; 
   
  
  /** Stores the attribute to use the blocking criterion on */
  protected int m_attrId;

  /** A hash to keep the good pairs from the last round */
  
  /** The type of blocker */
  public abstract int type();
  

  /** Return a copy of this Blocker */
  public abstract Blocker clone();


  /** Temporarily store good pairs found in last map */
  HashSet<Integer> m_goodPairSet = null; 


  /** Set the attribute which should be used for blocking */
  public void setAttribute(int attrId) {
     m_attrId = attrId;
  }
  /** Get the attribute */
  public int attribute() {
    return m_attrId;
  }
  

  /** Given the data, construct the blocking */
  public void blockData(Instances instances) {
    blockData(instances, m_redBlue); 
  } 

  /** Given the data, construct the blocking */
  public void blockData(Instances instances, boolean redBlue) {
    m_redBlue = redBlue; 
    m_instances = instances;
    int numInstances = instances.numInstances();
    m_blockingMap = new BlockingMap(numInstances,
                                    blocksOverlap(), m_redBlue);
    
    // for all instances
    for (int idx = 0; idx < numInstances; idx++) {
      Object[] blocks = getBlocks(instances.instance(idx)); 
      m_blockingMap.putInstanceBlocks(idx, blocks);

      for (Object blockValue : blocks) { 
        m_blockingMap.putInstanceIdx(idx, blockValue);
      }
    }
    m_blockingMap.createPotentialPairs(instances.numInstances());
  }


  /** Return the list of blocks for a given instance */
  public abstract Object[] getBlocks (Instance instance);
  
  
  /** Given two instances, do they fall into the same block? */
  public abstract boolean sameBlock(Instance instance1, Instance instance2);


  /** Are blocks overlapping or disjoint for this blocker?
   *  In other words, can an instance belong to more than one block? */
  public abstract boolean blocksOverlap(); 

  
  /** Return the blocking map   */
  public BlockingMap getBlockingMap() {
    return m_blockingMap;
  }


  /** Get an estimate of the number of examples that this
    * blocker will cover based on duplicates that have
    * already been found
    */
  public double getCoverEstimate(int[] goodPairs,  int[] goodFoundPairs,
                                 int[] allFoundPairs) {
    return m_blockingMap.getCoverEstimate(goodPairs, goodFoundPairs, allFoundPairs);
  } 


  /** Update the list of real pairs and pairs found so far 
    * based on the dupes found by the blocker  */
  public void updatePairLists(int[] goodPairs,  int[][] goodFoundPairs,
                              int[][] allFoundPairs) {
    m_goodPairSet = m_blockingMap.getGoodPairs();
    m_blockingMap.updatePairLists(goodPairs, goodFoundPairs, allFoundPairs);
  }


  /** Get the list of duplicate pairs found by this blocker */
  public HashSet<Integer> getPairs() {
    return m_blockingMap.getPairs();
  }


  /** Return the covered pairs as an array */
  public int[] getPairsArray() {
    return m_blockingMap.getPairsArray();
  }
    
  
  /** Get the list of duplicate pairs found by this blocker */
  public HashSet<Integer> getGoodPairs() {
    return m_goodPairSet; 
  }
  

  /** Free up unnecessary memory */
  public void resetBlocker() {
    m_blockingMap = null;
    m_goodPairSet = null; 
  }

  
  /** toString */
  public String toString() {
    return Utils.removeSubstring(this.getClass().getName(),
                                 "weka.linkage.blocking.learn.")
      + "-" +
      (m_instances == null ? m_attrId : m_instances.attribute(m_attrId).name());
  } 

  public static Blocker forName(String blockerName, String[] options) throws Exception {
    return (Blocker)Utils.forName(Blocker.class,
                                  blockerName,
                                  options);
  }


  public static void main (String[] args) {
    Instances instances = null;
    String dataFile = "/u/mbilenko/weka/data/linkage/rest-nophone-1field.arff"; 
    try { 
      instances = new Instances(new BufferedReader (new FileReader(dataFile)));
    } catch (Exception e) {
      System.out.println("Couldn't get data from " + dataFile);
      System.exit(1); 
    }
    Instance instance = instances.instance(66); 
    
//     CommonTokenNGramBlocker blocker = new CommonTokenNGramBlocker();
//     blocker.setN(1);
//     HashSet<String> blocks1 = blocker.stringToTokenGrams(instance.stringValue(0));
//     for (String block : blocks1) {
//       System.out.print('\t');
//       System.out.println(block);
//     }

    CommonIntegerBlocker blocker = new CommonIntegerBlocker();
    blocker.setAttribute(0);
    
    Object[] blocks = blocker.getBlocks(instance);



    
    System.out.println(instance); 
    for (Object block : blocks) {
      System.out.print('\t');
      System.out.println(block);
    }
  } 

} 


















