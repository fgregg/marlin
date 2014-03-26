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
 *    LearnableBlocking.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import java.text.SimpleDateFormat;

import weka.linkage.*;
import weka.linkage.metrics.*;
import weka.linkage.blocking.learn.*;

/**
 * This class takes a set of records, amalgamates them into single
 * strings and creates an inverted index for that collection.  It then
 * can return the pairs of strings that are most alike.  Largely
 * borrowed from VectorSpaceMetric.
 *
 * @author Beena Kamath
 * @author Misha Bilenko
 */
public class ManualBlocking extends Blocking implements OptionHandler, Serializable {

  /** Currently blocked pairs */
  protected int[]  m_blockedPairs = null;

  /** Instances */
  protected Instances m_instances; 
  
  /** Number of Instances indeeded for generating pairs */
  protected int m_numInstances;

  /** The set of blockers to use for training */
  protected Blocker[] m_blockers = new Blocker[0];

  /** Corresponding attributes */
  protected double[] m_attrs = new double[0];

  /** The set of blockers to use for testing */
  protected ArrayList m_learnedBlockers;

  /** Statistics */
  protected int m_numTotalPairs = 0;                  
  protected int m_numGoodPairs = 0; 
  protected int m_numTruePairs = 0;

  protected int m_numTotalPairsTrain = 0;  // the overall number of pairs in the test split
  protected int m_numTotalPairsTest = 0;  // the overall number of pairs in the test split

  protected int m_numPotentialDupePairsTrain = 0;
  protected int m_numActualDupePairsTrain = 0;
  protected int m_numPotentialNonDupePairsTrain = 0;
  protected int m_numActualNonDupePairsTrain = 0;

  protected int m_numCurrentBlockers = 0;

  protected double m_trainTime = 0;
  protected double m_testTimeStart = 0;


  /** Construct a vector space from a given set of examples
   * @param strings a list of strings from which the inverted index is
   * to be constructed
   */
  public ManualBlocking() {
    // fixing a mem leak : force free the blockers
    m_learnedBlockers = null;
    m_blockedPairs = null;
  }


  /** Learn the set of blockers to use */ 
  public void learnBlocking(Instances data) {
    System.out.println("Not learning anything here"); 
  }


  /** Given a list of strings, build the vector space */
  public void buildIndex(Instances instances) throws Exception {
    m_instances = instances;
    m_blockedPairs = new int[0]; 
    m_numInstances = instances.numInstances();
    m_numTotalPairsTest = instances.numInstances() * (instances.numInstances() - 1) / 2;
    resetStatistics();
    int[] goodPairs = getRealPairs(instances);
    
    m_numTruePairs = goodPairs.length;
    System.out.println("\n" + BasicDeduper.getTimestamp()
                       + ": Starting to block "
                       + " on " + instances.numInstances() + " instances; "   
                       + m_numTotalPairsTest + " potential pairs; "
                       + m_numTruePairs + " true pairs\n");

    System.out.println(m_blockers);
    System.out.println(m_attrs);
    if (m_blockers.length != m_attrs.length) {
      System.out.println("\n\n\nATTRIBUTES NOT SAME LENGTH AS BLOCKERS!!!");
    }
    
    // reset the maps of all blockers
    for (int i = 0; i < m_blockers.length; i++) {
      m_blockers[i].resetBlocker();
      m_blockers[i].setAttribute((int)m_attrs[i]); 
    }

    if (m_blockers.length > 1) { 
      ComboBlocker blocker = new ComboBlocker(m_blockers);

      blocker.blockData(instances);
      HashSet<Integer> blockedPairSet = blocker.getPairs();
      int numBlockedPairs = blockedPairSet.size();
      
      for (int j = 0; j < goodPairs.length; j++) {
        if (blockedPairSet.contains(goodPairs[j])) {
          m_numGoodPairs++;
        }
      }
    
      m_numTotalPairs = blockedPairSet.size();
      m_numCurrentBlockers = 1; 
      accumulateStatistics();
    } else {
      Blocker blocker = m_blockers[0];

      blocker.blockData(instances);

      int[] blockedPairs = blocker.getPairsArray();
      m_numTotalPairs = blockedPairs.length; 

      System.out.println(blocker.toString() + " added "
                         + m_numTotalPairs + "pairs");      

      m_numGoodPairs = 0; 
      for (int pairCode : blockedPairs) {
        if (Arrays.binarySearch(goodPairs, pairCode) >= 0) {
          m_numGoodPairs++;
        }
      } 
      accumulateStatistics();
      
    } 


  }


  /** Return n most similar pairs
   */
  public InstancePair[] getMostSimilarPairs(int numPairs) {
    int i = 0;
    int numBlockedPairs = m_blockedPairs.length;
    InstancePair [] pairs = new InstancePair[numPairs]; 
    while (i < numBlockedPairs && i < numPairs) {
      int pairCode = m_blockedPairs[i]; 
      Integer idx1 = new Integer(pairCode / m_numInstances);
      Integer idx2 = new Integer(pairCode % m_numInstances);
      Instance instance1 = m_instances.instance(idx1);
      Instance instance2 = m_instances.instance(idx2);
      InstancePair pair = new InstancePair(instance1, instance2, true, 0);
      pairs[i] = pair;
      ++i;
    }
    return pairs; 
  } 
  

  /** Reset the current statistics */
  protected void resetStatistics() {
    m_statistics = new ArrayList();     
    m_numGoodPairs = 0;    
    m_numTotalPairs = 0;
    m_numCurrentBlockers = 0; 
    m_testTimeStart = System.currentTimeMillis();
  }



     /**
    *  Accumulate statistics
    */
  protected void accumulateStatistics() {
    Object[] currentStats = new Object[21];

    double precision = m_numTotalPairs>0 ? (m_numGoodPairs+0.0)/m_numTotalPairs : 0.0; 
    double recall = (m_numGoodPairs+0.0)/m_numTruePairs;
    double reductionRatio = 1.0-m_numTotalPairs/(m_numTotalPairsTest+0.0);

    double fmeasure = 0;
    if (precision + recall > 0) {  // avoid divide by zero in the p=0&r=0 case
      fmeasure = 2 * (precision * recall) / (precision + recall);
    }

    System.out.println("\t adding stats:  R=" +(float)recall
                       + "\tRR=" + (float)reductionRatio 
                       + "\tP=" + (float)precision +
                       "\t" + m_numGoodPairs + "("
                       + m_numTruePairs + ")/" + m_numTotalPairs + "\tFM=" + fmeasure); 
                       
    int statIdx = 0;
    currentStats[statIdx++] = new Double(m_numInstances);

    // Accuracy statistics          
    currentStats[statIdx++] = new Double(recall);
    currentStats[statIdx++] = new Double(precision);
    currentStats[statIdx++] = new Double(reductionRatio);         
    currentStats[statIdx++] = new Double(fmeasure);

    // Dupe density statistics
    currentStats[statIdx++] = new Double(m_numTotalPairsTrain);
    currentStats[statIdx++] = new Double(m_numPotentialDupePairsTrain);
    currentStats[statIdx++] = new Double(m_numActualDupePairsTrain);
    currentStats[statIdx++] = new Double(m_numPotentialNonDupePairsTrain);
    currentStats[statIdx++] = new Double(m_numActualNonDupePairsTrain);
    currentStats[statIdx++] = new Double((m_numActualNonDupePairsTrain > 0) ?
                                         ((m_numActualDupePairsTrain+0.0)/m_numActualNonDupePairsTrain) : 0);
    currentStats[statIdx++] = new Double((m_numPotentialDupePairsTrain+0.0)/m_numTotalPairsTrain);

    currentStats[statIdx++] = new Double(m_numTotalPairsTest);
    currentStats[statIdx++] = new Double(m_numTruePairs);
    currentStats[statIdx++] = new Double((m_numTruePairs + 0.0)/m_numTotalPairsTest);
    currentStats[statIdx++] = new Double((m_numTotalPairs-m_numGoodPairs + 0.0)/(m_numTotalPairsTest-m_numTruePairs));

    // Predicate statistics
    currentStats[statIdx++] = new Double(m_numGoodPairs);
    currentStats[statIdx++] = new Double(m_numTotalPairs);
    currentStats[statIdx++] = new Double(m_numCurrentBlockers);


    // Timing statistics
    currentStats[statIdx++] = new Double(m_trainTime);
    currentStats[statIdx++] = new Double((System.currentTimeMillis() - m_testTimeStart)/1000.0);

    m_statistics.add(currentStats);
  }


  /** Given a test set, calculate the number of true pairs
   * @param instances a set of objects, class has the true object ID
   * @returns the number of true same-class pairs
   */
  protected int numTruePairs(Instances instances) {
    int numTruePairs = 0;
    HashMap<Double,Integer> classCountMap = new HashMap<Double,Integer>();

    for (int i = 0; i < instances.numInstances(); i++) {
      Instance instance = instances.instance(i);
      Double classValue = new Double(instance.classValue());
      if (classCountMap.containsKey(classValue)) {
        Integer counts = (Integer) classCountMap.get(classValue);
        classCountMap.put(classValue, new Integer(counts.intValue() + 1));
      } else {
        classCountMap.put(classValue, new Integer(1));
      }
    }

    // calculate the number of pairs
    for (Integer counts : classCountMap.values()) { 
      numTruePairs += counts * (counts - 1) / 2;
    }

    return numTruePairs;
  }

  /** Get the real duplicate pairs
   */
  protected int[] getRealPairs(Instances instances) {
    BlockingMap realBlockingMap = new BlockingMap(instances.numInstances(), false, true);
    for (int i = 0; i < instances.numInstances(); i++) {
      Instance instance = instances.instance(i);
      if (instance.classIsMissing()) {
        System.err.println("Instance " + instance + " has missing class!!!");
        continue;
      }
      Double classValue = new Double(instance.classValue());
      realBlockingMap.putInstanceIdx(i, classValue);
    }
    realBlockingMap.createPotentialPairs(instances.numInstances());

    HashSet<Integer> realPairSet = realBlockingMap.getPairs();
    int[] pairs = new int[realPairSet.size()];
    int i = 0; 
    for (int pairIdx : realPairSet) {
      pairs[i++] = pairIdx;
    }
    Arrays.sort(pairs);

    return pairs;
  }


  /** Set/get the baseline blockers */
  public void setBlockers(Blocker[] blockers) { m_blockers = blockers; } 
  public Blocker[] getBlockers() { return m_blockers; }


  public String getAttributeIndeces() {
    StringBuffer buf = new StringBuffer();
    if (m_attrs != null) 
      for (int i=0; i < m_attrs.length; i++) {
	buf.append(m_attrs[i]);
	if (i != (m_attrs.length -1)) 
	  buf.append(" ");
      }
    return buf.toString();
  }
  
  /**
   * Set the value of Indeces.
   *
   * @param indices Value to assign to
   * Indeces.
   */
  public void setAttributeIndeces(String indices) {
    m_attrs = parseIndeces(indices);
  }
  
  /** 
   * Parse a string of doubles separated by commas or spaces into a sorted array of doubles
   */
  protected double[] parseIndeces(String indices) {
    StringTokenizer tokenizer = new StringTokenizer(indices," ,\t");
    double[] result = null;
    int count = tokenizer.countTokens();
    if (count > 0)
      result = new double[count];
    else
      return null;
    int i = 0;
    while(tokenizer.hasMoreTokens()) {
      result[i] = Double.parseDouble(tokenizer.nextToken());
      i++;
    }
    Arrays.sort(result);
    return result;
  }


  /**
   * Gets the current settings of the Blocker
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [250];
    int current = 0;

    options[current++] = "-B" + m_blockers.length;
    for (int i = 0; i < m_blockers.length; i++) {
      String blockerName = Utils.removeSubstring(m_blockers[i].getClass().getName(),
                                                 "weka.linkage.blocking.learn.");
      options[current++] = Utils.removeSubstring(blockerName, 
                                                 "Blocker");
      if (m_blockers[i] instanceof OptionHandler) {
        String[] blockerOptions = ((OptionHandler)m_blockers[i]).getOptions();
        for (int j = 0; j < blockerOptions.length; j++) {
          options[current++] = blockerOptions[j];
        }
      }
    }

    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }


  /**
   * Parses a given list of options. Valid options are:<p>
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


















