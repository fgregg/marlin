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
public class LearnableBlocking extends Blocking implements OptionHandler, Serializable {

  /** Should sampling be used? */
  protected boolean m_sample = false; 
  
  /** Class used for creating a random subsample of training data */
  protected BlockingSampler m_sampler = new BlockingSamplerByClass(); 
  
  /** Currently blocked pairs */
  protected int[]  m_blockedPairs = null;

  /** Instances */
  protected Instances m_instances; 
  
  /** Number of Instances indeeded for generating pairs */
  protected int m_numInstances;

  /** The Blocking Learner to use */
  protected BlockingLearner m_learner = new SetCoverBlockingLearner();

  /** The set of blockers to use for training */
  protected Blocker[] m_blockers = new Blocker[0];

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
  public LearnableBlocking() {
    // fixing a mem leak : force free the blockers
    m_learnedBlockers = null;
    m_blockedPairs = null;
  }


  /** Learn the set of blockers to use */ 
  public void learnBlocking(Instances data) {
    System.out.println("\n" + BasicDeduper.getTimestamp()
                       + ": Starting to learn blocking "
                       + " using " + data.numInstances() + " instances\n");
    long trainTimeStart = System.currentTimeMillis();

    if (m_sample) {
      data = m_sampler.getSample(data);
    }

    for (int i = 0; i < data.numInstances(); i++) {
      Instance instance = data.instance(i);
      for (int j = 0; j < data.numAttributes(); j++) {
        if (j != data.classIndex()) {
          String value = instance.stringValue(j);
          value = value.toLowerCase();
          char[] val = value.toCharArray();
          StringBuffer newVal = new StringBuffer();

          boolean lastSpace = false; 
          for (int k = 0; k < val.length; k++) {
            if (Character.isLetterOrDigit(val[k])) {
              newVal.append(val[k]);
              lastSpace = false; 
            } else if (!lastSpace) {
              newVal.append(' ');
              lastSpace = true; 
            } 
          }

          String cleanedVal = newVal.toString();
          instance.setValue(j,cleanedVal.trim()); 
        }
      } 
      instance.setWeight(i);
    }
    
    m_numTotalPairsTrain = (int) (0.5 * data.numInstances() *
                                  (data.numInstances() -1));
    m_numPotentialDupePairsTrain = numTruePairs(data);
    m_numPotentialNonDupePairsTrain =
      m_numTotalPairsTrain - m_numPotentialDupePairsTrain;

    // fixing a mem leak : force free the blockers
    m_learnedBlockers = null;
    m_learnedBlockers = m_learner.getBlockers(m_blockers, data);

    
   //  m_numActualDupePairsTrain = m_metric.getNumActualPosPairs();
    // m_numActualNonDupePairsTrain = m_metric.getNumActualNegPairs();
    m_trainTime = (System.currentTimeMillis() - trainTimeStart)/1000.0;

    System.out.println(BasicDeduper.getTimestamp() + ": Done learning blocking");
  }


     /** Given a list of strings, build the vector space
   */
  public void buildIndex(Instances instances) throws Exception {
    m_instances = instances;
    m_numInstances = instances.numInstances();
    m_numTotalPairsTest = (int)(0.5 * instances.numInstances() * (instances.numInstances() - 1));
    m_numCurrentBlockers = m_learnedBlockers.size();
    resetStatistics();
    
    int[] goodPairs = getRealPairs(instances);
    boolean[] blockedPairs = new boolean[m_numTotalPairsTest];

    //    HashSet<Integer> blockedPairSet = new HashSet<Integer>();
    

    for (int i = 0; i < instances.numInstances(); i++) {
      Instance instance = instances.instance(i);
      for (int j = 0; j < instances.numAttributes(); j++) {
        if (j != instances.classIndex()) {
          String value = instance.stringValue(j);
          value = value.toLowerCase();
          char[] val = value.toCharArray();
          StringBuffer newVal = new StringBuffer();

          boolean lastSpace = false; 
          for (int k = 0; k < val.length; k++) {
            if (Character.isLetterOrDigit(val[k])) {
              newVal.append(val[k]);
              lastSpace = false; 
            } else if (!lastSpace) {
              newVal.append(' ');
              lastSpace = true; 
            } 
          }

          String cleanedVal = newVal.toString();
          instance.setValue(j,cleanedVal.trim()); 
        }
      } 
      instance.setWeight(i);
    }

    m_numTruePairs = goodPairs.length;
    System.out.println("\n" + BasicDeduper.getTimestamp()
                       + ": Starting to block "
                       + " on " + instances.numInstances() + " instances; "   
                       + m_numTotalPairsTest + " potential pairs; "
                       + m_numTruePairs + " true pairs\n");

    // reset the maps of all blockers
    for (int i = 0; i < m_learnedBlockers.size(); i++) {
      Blocker blocker = (Blocker) m_learnedBlockers.get(i);
      blocker.resetBlocker();
    }

    // go through blockers one by one
    for (int i = 0; i < m_learnedBlockers.size(); i++) {
      Blocker blocker = (Blocker) m_learnedBlockers.get(i);
      blocker.blockData(instances);
      int[] newBlockedPairs = blocker.getPairsArray();

//       int numOldPairs = blockedPairSet.size();
//      blockedPairSet.addAll(newBlockedPairSet);
      int numUnseenPairs = 0;
      for (int newPair : newBlockedPairs) {
        if (blockedPairs[newPair] == false) {
          blockedPairs[newPair] = true;
          numUnseenPairs++;
          m_numTotalPairs++;

          if (Arrays.binarySearch(goodPairs,newPair) >= 0) {
            m_numGoodPairs++;
          }
        }
      }
      System.out.println(i + ". " + blocker.toString() + " added "
                         + newBlockedPairs.length + "; " 
                         + numUnseenPairs + " are new. "
                         + " Total pairs now: " + m_numTotalPairs + "; "
                         + m_numGoodPairs + " are good"); 
      

//       System.out.println(i + ". " + blocker.toString() + " added "
//                          + newBlockedPairSet.size() + "; " +
//                          (blockedPairSet.size() - numOldPairs) + " are new. "
//                          + " Total pairs now: " + blockedPairSet.size()); 

      newBlockedPairs = null; 
      blocker.resetBlocker(); 
    }

    
    System.out.println("Done blocking with " + m_numTotalPairs + " pairs");

    m_numGoodPairs = 0; 
    for (int pairIdx : goodPairs) {
      if (blockedPairs[pairIdx]) { 
        m_numGoodPairs++;
      } else {
       //  int idx1 = 1+(int)Math.floor((-1.0+Math.sqrt(1.0+8.0*pairIdx))/2.0);
//         int idx2 = pairIdx - idx1*(idx1-1)/2;

//         Instance instance1 = instances.instance(idx1);
//         Instance instance2 = instances.instance(idx2);

        
       
//         for (int i = 0; i < m_learnedBlockers.size(); i++) {
//           Blocker blocker = (Blocker) m_learnedBlockers.get(i);
//           if (blocker instanceof TFIDFBlocker)  continue;
          
//           if (blocker.sameBlock(instance1, instance2)) {

//             System.out.println("missed pair " + pairIdx + ": " + instance1);
//             System.out.println("\t\t" + instance2);
//             System.out.println(blockedPairSet.contains(pairIdx)); 
          

//             Object[] blocks1 = blocker.getBlocks(instance1);
//             Object[] blocks2 = blocker.getBlocks(instance2);
//             System.out.print("\t\t" + blocker + " says " + 
//                              blocker.sameBlock(instance1, instance2) + ": {");
//             for (Object block1 : blocks1)  System.out.print(block1 + ", ");
//             System.out.print("}, {");
//             for (Object block2 : blocks2)  System.out.print(block2 + ", ");
//             System.out.println("}");
//           }
//         }
        
      } 
    } 
    accumulateStatistics();
    
    // fixing a mem leak : force free the blockers
    m_learnedBlockers = null;

    // print out errors
    //       for (int pairIdx : goodPairs) {
    //         if (Arrays.binarySearch(m_blockedPairs, pairIdx) < 0) {

    //           int i1 = 1+(int)Math.floor((-1.0+Math.sqrt(1.0+8*pairIdx))/2.0);
    //           int i2 = pairIdx - i1*(i1-1)/2;
    //           int trueIdx = i1*(i1-1)/2 + i2;
    //           if (trueIdx != pairIdx) System.out.println("MESSED UP INDECES!"); 

    //           System.out.print("MISSED " + pairIdx + "\t:");
          
    //           System.out.print(i1 + ": ");
    //           System.out.println(instances.instance(i1));
    //           System.out.print("\t" + i2 + ": ");
    //           System.out.println(instances.instance(i2));
    // //           for (int i = 0; i < m_learnedBlockers.size(); i++) {
    // //             Blocker blocker = (Blocker) m_learnedBlockers.get(i);
    // //             Object[] blocks1 = blocker.getBlocks(instances.instance(i1));
    // //             Object[] blocks2 = blocker.getBlocks(instances.instance(i2));
    // //             System.out.print("\t1: "); 
    // //             for (Object b1 : blocks1) System.out.print(b1 + ", ");
    // //             System.out.print("\n\t2: "); 
    // //             for (Object b2 : blocks2) System.out.print(b2 + ", ");
    // //             System.out.print("\n"); 
    // //           }
    //         }
    //       }

//     System.out.println("Done blocking with " + blockedPairSet.size() + " pairs");

//     m_numTotalPairs =  blockedPairSet.size();
//     m_numGoodPairs = 0; 
//     for (int pairCode : blockedPairSet) {
//       if (Arrays.binarySearch(goodPairs, pairCode) >= 0) {
//         m_numGoodPairs++;
//       }
//     } 
//     accumulateStatistics();
    
//     // fixing a mem leak : force free the blockers
//     m_learnedBlockers = null;
  }


//    /** Given a list of strings, build the vector space
//    */
//   public void buildIndexOLD(Instances instances) throws Exception {
//     m_instances = instances;
//     m_blockedPairs = new int[0]; 
//     m_numInstances = instances.numInstances();
//     m_numTotalPairsTest = (int)(0.5 * instances.numInstances() * (instances.numInstances() - 1));
//     resetStatistics();
//     int[] goodPairs = getRealPairs(instances);
//     m_numTruePairs = goodPairs.length;
//     System.out.println("\n" + BasicDeduper.getTimestamp()
//                        + ": Starting to block "
//                        + " on " + instances.numInstances() + " instances; "   
//                        + m_numTotalPairsTest + " potential pairs; "
//                        + m_numTruePairs + " true pairs\n");

//     // for every block, add pairs that haven't been seen before
//     if (m_learnedBlockers.size() > 1 ||
//         !(m_learnedBlockers.get(0) instanceof WeightedBlocker)) { 

//       // reset the maps of all blockers
//       for (int i = 0; i < m_learnedBlockers.size(); i++) {
//         Blocker blocker = (Blocker) m_learnedBlockers.get(i);
//         blocker.resetBlocker();
//       }

//       // go through blockers one by one
//       for (int i = 0; i < m_learnedBlockers.size(); i++) {
//         Blocker blocker = (Blocker) m_learnedBlockers.get(i);
//         blocker.blockData(instances);
//         int[] blockedPairs = blocker.getPairs();
//         int numBlockedPairs = blockedPairs.length; 
        
//         boolean[] newPairs = new boolean[numBlockedPairs];
//         Arrays.fill(newPairs, false); 
      
//         for (int j =0; j < blockedPairs.length; j++) {
//           int idx = Arrays.binarySearch(m_blockedPairs, blockedPairs[j]); 
//           if (idx < 0) { // don't care about previously seen pairs
//             if (Arrays.binarySearch(goodPairs, blockedPairs[j]) >= 0) {
//               m_numGoodPairs++;
//             }
//             newPairs[j] = true; 
//           }
//         }

//         int numNewPairs = 0; 
//         for (int j = 0; j < numBlockedPairs; j++) {
//           if (newPairs[j])
//             numNewPairs++; 
//         }

//         int[] newPairIdxs = new int[numNewPairs];
//         int idx = 0; 
//         for (int j = 0; j < numBlockedPairs; j++) {
//           if (newPairs[j])
//             newPairIdxs[idx++] = blockedPairs[j]; 
//         }
//         blockedPairs = null; 

//         int[] newBlockedPairs = new int[m_blockedPairs.length + numNewPairs];
//         System.arraycopy(m_blockedPairs, 0, newBlockedPairs, 0, m_blockedPairs.length);
//         System.arraycopy(newPairIdxs, 0, newBlockedPairs, m_blockedPairs.length, numNewPairs);
//         Arrays.sort(newBlockedPairs);
//         m_blockedPairs = newBlockedPairs;
      
//         m_numTotalPairs = m_blockedPairs.length;
//         m_numCurrentBlockers = i;
//         System.out.println(i + ". " + blocker); 
//         accumulateStatistics();
//       }

//       // print out errors
// //       for (int pairIdx : goodPairs) {
// //         if (Arrays.binarySearch(m_blockedPairs, pairIdx) < 0) {

// //           int i1 = 1+(int)Math.floor((-1.0+Math.sqrt(1.0+8*pairIdx))/2.0);
// //           int i2 = pairIdx - i1*(i1-1)/2;
// //           int trueIdx = i1*(i1-1)/2 + i2;
// //           if (trueIdx != pairIdx) System.out.println("MESSED UP INDECES!"); 

// //           System.out.print("MISSED " + pairIdx + "\t:");
          
// //           System.out.print(i1 + ": ");
// //           System.out.println(instances.instance(i1));
// //           System.out.print("\t" + i2 + ": ");
// //           System.out.println(instances.instance(i2));
// // //           for (int i = 0; i < m_learnedBlockers.size(); i++) {
// // //             Blocker blocker = (Blocker) m_learnedBlockers.get(i);
// // //             Object[] blocks1 = blocker.getBlocks(instances.instance(i1));
// // //             Object[] blocks2 = blocker.getBlocks(instances.instance(i2));
// // //             System.out.print("\t1: "); 
// // //             for (Object b1 : blocks1) System.out.print(b1 + ", ");
// // //             System.out.print("\n\t2: "); 
// // //             for (Object b2 : blocks2) System.out.print(b2 + ", ");
// // //             System.out.print("\n"); 
// // //           }
// //         }
// //       }

//     } else {  // We have just a single blocker - must be weighted blocker. Block, then go over pairs
//       WeightedBlocker blocker = (WeightedBlocker) m_learnedBlockers.get(0);
//       blocker.blockData(instances, false);
//       m_numCurrentBlockers = blocker.getBlockers().length; 
      
//       for (double threshold = 100.0; threshold > -10; threshold = threshold -1) { 
//         blocker.setThreshold(threshold); 
//         int[] posPairs = blocker.getPairs();

//         m_numGoodPairs = 0;
//         m_numTotalPairs = posPairs.length; 

//         HashSet<Integer> seenGoodPairs = new HashSet<Integer>(); 
//         for (int pairCode : posPairs) {
//           if (Arrays.binarySearch(goodPairs, pairCode) >= 0) { // good pair
//             ++m_numGoodPairs;
//           }
//         }

//         System.out.println("Threshold=" + threshold +
//                            "\tgoodPairs=" + m_numGoodPairs + "/" + m_numTruePairs +
//                            "\ttotalPairs=" + m_numTotalPairs);
//         accumulateStatistics();
//         if (m_numGoodPairs == m_numTruePairs) {
//           break;
//         }
//       }
//     }
//     System.out.println("Done blocking with " + m_blockedPairs.length + " pairs"); 
//     // fixing a mem leak : force free the blockers
//     m_learnedBlockers = null;
//   }


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

    m_numCurrentBlockers = m_learnedBlockers.size(); 
    double precision = m_numTotalPairs>0 ? (m_numGoodPairs+0.0)/m_numTotalPairs : 0.0; 
    double recall = (m_numGoodPairs+0.0)/m_numTruePairs;
    double reductionRatio = 1.0-m_numTotalPairs/(m_numTotalPairsTest+0.0);

    double fmeasure = 0;
    if (precision + recall > 0) {  // avoid divide by zero in the p=0&r=0 case
      fmeasure = 2 * (precision * recall) / (precision + recall);
    }

    System.out.println("\t adding stats:  RR=" +(float)reductionRatio
                       + "\tP=" + (float)precision                        
                       + "\tR=" + (float)recall +
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
    BlockingMap realBlockingMap = new BlockingMap(instances.numInstances(),
                                                  true, true);
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


  /** Set/get the blocking learner */
  public void setBlockingLearner(BlockingLearner learner) { m_learner = learner; } 
  public BlockingLearner getBlockingLearner() { return m_learner; } 

  /** Set/get the baseline blockers */
  public void setBlockers(Blocker[] blockers) { m_blockers = blockers; } 
  public Blocker[] getBlockers() { return m_blockers; } 

  /** Turn sampling on/off */
  public boolean getSample() { return m_sample; }
  public void setSample(boolean s) { m_sample = s; }

  /** Set/get the sampler */
  public BlockingSampler getSampler() { return m_sampler; }
  public void setSampler(BlockingSampler s) { m_sampler = s; }


  /**
   * Gets the current settings of the Blocker
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [250];
    int current = 0;

    if (m_sample) { 
      options[current++] = "-S";
      String samplerName = Utils.removeSubstring(m_sampler.getClass().getName(),
                                                 "weka.linkage.blocking.learn.");
      options[current++] = Utils.removeSubstring(samplerName, "BlockingSampler"); 
      if (m_sampler instanceof OptionHandler) {
        String[] samplerOptions = ((OptionHandler)m_sampler).getOptions();
        for (int i = 0; i < samplerOptions.length; i++) {
          options[current++] = samplerOptions[i];
        }
      } 
    }

    options[current++] = "-L";
    String learnerName = Utils.removeSubstring(m_learner.getClass().getName(),
                                               "weka.linkage.blocking.learn.");
    options[current++] = Utils.removeSubstring(learnerName, "BlockingLearner");
    if (m_learner instanceof OptionHandler) {
      String[] learnerOptions = ((OptionHandler)m_learner).getOptions();
      for (int i = 0; i < learnerOptions.length; i++) {
        options[current++] = learnerOptions[i];
      }
    } 

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


















