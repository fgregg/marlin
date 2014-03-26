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
 *    BlockingMap.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking.learn;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import weka.linkage.*;

/**
 * This class describes a basic data structure for storing a 
 * Duplicate pair map. This is used in the inverted index of 
 * Blocking. Largely borrowed from PairwiseSelector. 
 *
 * @author Beena Kamath, Misha Bilenko */


import java.io.*;

public class BlockingMap {

  /** A hashmap where Instances are grouped based on their blockid */
  protected HashMap<Object,int[]> m_blockInstanceMap = null;

  /** A reverse map where each instance is mapped to a list of blocks */
  protected Object[][] m_instanceBlocks = null; 

  /** Are the blocks overlapping or not? */
  protected boolean m_overlapping; 

  /** Choose between Chvatal and RedBlue */
  protected boolean m_redBlue = false;

  /** Cost of node if using RedBlue Estimation */
  protected int m_redBlueCost = -1;

  /** All good pairs found by this blocker */
  int[] m_goodBlockedPairs = null;
  HashSet<Integer> m_goodPairSet = null;

  /** A list with all the positive examples as TrainingPair's 
   *  not used due to memory problems
   */
  // protected HashSet m_posPairList = null;

  /** A list with a sufficient pool of negative examples as TrainingPair's 
   *  not used due to memory problems
   */
  // protected HashSet m_negPairList = null;

  /** The number of possible same-block pairs */
  protected int m_numSameBlockPairs = 0;

  /** Number of instances in this map */
  protected int m_numInstances;

  public BlockingMap(int numInstances, boolean overlapping, boolean redBlue) {
    m_blockInstanceMap = new HashMap<Object,int[]>();
    m_instanceBlocks = new Object[numInstances][0]; 
    m_numInstances = numInstances;
    m_numSameBlockPairs = 0;
    m_overlapping = overlapping; 
    m_redBlue = redBlue;
    m_redBlueCost = -1;
  }


  /** return the number of instances contained in this map */
  public int getNumInstances() {
    return m_numInstances;
  }

  /** Return the actual map */
  public HashMap<Object,int[]>  getBlockInstanceMap() {
    return m_blockInstanceMap;
  }

  /** Return the instances and corresponding blocks */
  public Object[][] getInstanceBlocks() {
    return m_instanceBlocks;
  }


  /** Put the instance in its correspoding block */
  public void putInstanceIdx(int instanceIdx, Object blockValue) {
    int[] instanceIndeces =  m_blockInstanceMap.get(blockValue);
    if (blockValue instanceof String && ((String)blockValue).length() == 0) {
      return;
    }
    
    if (instanceIndeces != null) { // seen this blockValue before
      int[] newInstanceIndeces = new int[instanceIndeces.length+1];
      System.arraycopy(instanceIndeces, 0, newInstanceIndeces, 0,
                       instanceIndeces.length);
      newInstanceIndeces[instanceIndeces.length] = instanceIdx;
      instanceIndeces = newInstanceIndeces; 
    } else {   // new blockValue
      instanceIndeces = new int[1];
      instanceIndeces[0] = instanceIdx;
    }

    m_blockInstanceMap.put(blockValue, instanceIndeces);
  }

  /** Store the block IDs for the instance */
  public void putInstanceBlocks(int instanceIdx, Object[] blocks) {
    m_instanceBlocks[instanceIdx] = blocks; 
  }

  /** Add a new block ID for the instance */
  public void addInstanceBlock(int instanceIdx, Object block) {
    Object[] oldBlocks = m_instanceBlocks[instanceIdx];
    Object[] newBlocks = new Object[oldBlocks.length+1];
    System.arraycopy(oldBlocks, 0, newBlocks, 0, oldBlocks.length);
    newBlocks[oldBlocks.length] = block;
    m_instanceBlocks[instanceIdx] = newBlocks;
  } 


  /** Calculate the number of potential positive pairs */
  public void createPotentialPairs(int numInstances) {
    m_numInstances = numInstances;
    m_numSameBlockPairs = 0;

    // go through all blocks and add the total number of same-block pairs
    for (int[] instanceIndeces : m_blockInstanceMap.values()) { 
      m_numSameBlockPairs += instanceIndeces.length * (instanceIndeces.length - 1) / 2;
    }
  }


  /** Return the list of positive duplicate pairs found */
  public HashSet<Integer> getPairs() {
    HashSet<Integer> pairSet = new HashSet<Integer>();
    
    // go through lists of instances for each block
    for (int[] instanceIndeces : m_blockInstanceMap.values()) {
      // create a list of *all* positive pairs
      int blockSize = instanceIndeces.length; 
      for (int i = 0; i < blockSize-1; i++) {
        int instanceIdx1 = instanceIndeces[i];
        for (int j = i+1; j < blockSize; j++) {
          int instanceIdx2 = instanceIndeces[j];
          int pairCode = genPairCode(instanceIdx1, instanceIdx2);
          pairSet.add(pairCode);
        }
      }
    }

    return pairSet; 
  }

  
  /** Return the covered pairs as an array */
  public int[] getPairsArray() {
    int numTotalPairs = m_numInstances * (m_numInstances-1)/2;
    boolean[] coveredPairs = new boolean[numTotalPairs];
    Arrays.fill(coveredPairs, false); 
      
    // go through lists of instances for each block
    for (int[] instanceIndeces : m_blockInstanceMap.values()) {
      // create a list of *all* positive pairs
      int blockSize = instanceIndeces.length; 
      for (int i = 0; i < blockSize-1; i++) {
        int instanceIdx1 = instanceIndeces[i];
        for (int j = i+1; j < blockSize; j++) {
          int instanceIdx2 = instanceIndeces[j];
          int pairCode = genPairCode(instanceIdx1, instanceIdx2);
          coveredPairs[pairCode] = true; 
        }
      }
    }

    int numCoveredPairs = 0;
    for (int i = 0; i < numTotalPairs; i++) { 
      if (coveredPairs[i])
        numCoveredPairs++;
    }

    System.out.println("\tFound " + numCoveredPairs + " actual pairs"); 

    int[] pairs = new int[numCoveredPairs];
    int j = 0;
    for (int i = 0; i < numTotalPairs; i++) { 
      if (coveredPairs[i]) { 
        pairs[j++] = i;
      }
    }

    return pairs; 
  }


  /** Return the list of TRUE positive duplicate pairs found */
  public HashSet<Integer> getGoodPairs() {
    if (m_goodPairSet == null) {
      m_goodPairSet = new HashSet<Integer>(m_numSameBlockPairs);
      for (int pairIdx : m_goodBlockedPairs) { 
        m_goodPairSet.add(pairIdx);
      }
    }
    return m_goodPairSet;
  } 

  

  /** Get the cover estimate for pairs contained in this blocking.
   */
  public double getCoverEstimate(int[] goodPairs,
                                 int[] goodFoundPairs,
                                 int[] allFoundPairs) {
    if (m_redBlue) {
      return getCoverEstimateRedBlue(goodPairs, goodFoundPairs);
    } else {
      return getCoverEstimateChvatal(goodPairs, goodFoundPairs, allFoundPairs);
    }
  }


  /** Get the cover estimate for pairs contained in this blocking.
   *  The cover estimate is NumNewPositives/Cost of Negatives
  */
  public double getCoverEstimateRedBlue(int[] goodPairs, 
		                        int[] goodFoundPairs) {
    //    int [] seenPairs = new int[0];
    boolean[] seenPairs = new boolean[m_numInstances * (m_numInstances-1)/2];
    HashSet<Integer> seenPairSet = new HashSet<Integer>(); 
      
    int numNewPositives = 0;
    int numOldPositives = 0; 
    int cost = 0; 
    HashSet<Integer> goodPairSet = new HashSet<Integer>();
    m_redBlueCost = 0;

    int blockIdx = 0;
    System.out.print(m_blockInstanceMap.size() + " blocks: "); 

    for (int[] instanceIndeces : m_blockInstanceMap.values()) {
      int blockSize = instanceIndeces.length;

      for (int i = 0; i < blockSize-1; i++) {
        int instanceIdx1 = instanceIndeces[i];
        for (int j = i+1; j < blockSize; j++) {
          int instanceIdx2 = instanceIndeces[j];
          int pairCode = genPairCode(instanceIdx1, instanceIdx2);

          if (seenPairs[pairCode] == false) {
            seenPairs[pairCode] = true;
            cost++; 

            // is this a good pair? 
            if (Arrays.binarySearch(goodPairs, pairCode) >= 0) {
              goodPairSet.add(pairCode);
              if (Arrays.binarySearch(goodFoundPairs, pairCode) < 0) {
                ++numNewPositives;
              } else {
                ++numOldPositives;
              }
            } else {  // not a good pair
              ++m_redBlueCost;
            }
          }
        }
      }
      blockIdx++; 
    }
    seenPairs = null;

    System.out.print((m_numInstances * (m_numInstances-1)/2) + "->");
    System.out.print(cost + " pairs; GOOD:" +
                       numNewPositives + " new, " +
                       (cost - m_redBlueCost - numNewPositives) + " old,"
                       + m_redBlueCost + " bad\t");
      
    m_goodBlockedPairs = new int[goodPairSet.size()];
    int i = 0; 
    for (Integer pairIdx : goodPairSet) {
      m_goodBlockedPairs[i++] = pairIdx;
    }


    double coverEstimate = (numNewPositives)
      /(m_redBlueCost + 50.0);
    
    System.out.print("\t" + numNewPositives + "/" + m_redBlueCost
                     + "=" + (float)coverEstimate + "\t"); 
    return coverEstimate;
  }


 
  /** Get the cover estimate for pairs contained in this blocking.
   *  The cover estimate is NumNewPositives/(NumNewPositives+NumNewNegatives)
  */
  public double getCoverEstimateChvatal(int[] goodPairs,
                                 int[] goodFoundPairs,
                                 int[] allFoundPairs) {
    int numNewPositives = 0;
    int numOldPositives = 0;
    int numNewNegatives = 0;
    int numOldNegatives = 0;
    int total = 0; 
    boolean allFoundInitialized = (allFoundPairs != null);
    HashSet<Integer> pairSet = new HashSet<Integer>();

    for (int[] instanceIndeces : m_blockInstanceMap.values()) {
      int blockSize = instanceIndeces.length; 
      for (int i = 0; i < blockSize-1; i++) {
        int instanceIdx1 = instanceIndeces[i];
        for (int j = i+1; j < blockSize; j++) {
          int instanceIdx2 = instanceIndeces[j];
          int pairCode = genPairCode(instanceIdx1, instanceIdx2);
          if (pairSet.contains(pairCode)) {
            continue;
          }

          boolean positive = (Arrays.binarySearch(goodPairs, pairCode) >= 0);
          if (positive) {
            if (Arrays.binarySearch(goodFoundPairs, pairCode) >= 0) {
              ++numOldPositives;
            } else {
              ++numNewPositives;
            }
          } else if (allFoundInitialized) {
            if (Arrays.binarySearch(allFoundPairs, pairCode) >= 0) {
              ++numOldNegatives;
            } else {
              ++numNewNegatives;
            }
          } else {  // not keeping track of negatives
            ++numNewNegatives;
          }

          pairSet.add(pairCode); 
          ++total; 
        }
      }
    }

    if (numNewPositives + numNewNegatives == 0) {
      System.err.println("\to new pairs found among " + total + " total pairs;" +
                         numOldPositives + " old positives found out of " +
                         + goodPairs.length + " true positives"); 
      return (double) 0.0;
    }

    System.out.println("\tNew +: " + numNewPositives +
                       "\tOld +: " + numOldPositives +
                       "\tNew -: " + numNewNegatives +
                       "\tOld -: " + numOldNegatives); 
                       
    return ((double)numNewPositives / (numNewPositives+numNewNegatives));
  }


  /** Update the list of real pairs and pairs found so far */
  public void updatePairLists(int[] goodPairs,
                              int[][] goodFoundPairs,
                              int[][] allFoundPairs) {
    boolean allFoundInitialized = (allFoundPairs != null);
    HashSet<Integer> newGoodFoundSet = new HashSet<Integer>();
    HashSet<Integer> newAllFoundSet = new HashSet<Integer>();  
    
    if (m_redBlue == false) {
      for (int[] instanceIndeces : m_blockInstanceMap.values()) {
        int blockSize = instanceIndeces.length; 
        for (int i = 0; i < blockSize; i++) {
          int instanceIdx1 = instanceIndeces[i];
          for (int j = i+1; j < blockSize; j++) {
            int instanceIdx2 = instanceIndeces[j];
            int pairCode = genPairCode(instanceIdx1, instanceIdx2);
  
            // if good and unseen previously, add to good found pairs
            boolean good = (Arrays.binarySearch(goodPairs, pairCode) >= 0);
            if (good) { 
              int idx = Arrays.binarySearch(goodFoundPairs[0], pairCode);
              if (idx < 0) {
                newGoodFoundSet.add(pairCode);
              }
            }
  
            // if keeping track of *all* found pairs, do same
            if (allFoundInitialized) {
              int idx = Arrays.binarySearch(allFoundPairs[0], pairCode);
              if (idx < 0) { 
                newAllFoundSet.add(pairCode); 
              }
            } 
          }
        }
      }
    } else {
      for (int i = 0; i < m_goodBlockedPairs.length; i++) {
	int pairCode = m_goodBlockedPairs[i];
	boolean good = (Arrays.binarySearch(goodPairs, pairCode) >= 0);
	if (good) {
          int idx = Arrays.binarySearch(goodFoundPairs[0], pairCode);
	  if (idx < 0) {
	    newGoodFoundSet.add(pairCode);
	  }
	}
      }
    }
  
    // merge in new good found and new all found
    if (newGoodFoundSet.size() > 0) {
      int numOldPairs = goodFoundPairs[0].length;
      int numNewPairs = newGoodFoundSet.size();

      int[] newPairs = new int[numOldPairs + numNewPairs];
      System.arraycopy(goodFoundPairs[0], 0, newPairs, 0, numOldPairs);

      int j = numOldPairs; 
      for (Integer newPair : newGoodFoundSet) { 
        newPairs[j++] = newPair;
      }

      Arrays.sort(newPairs);
      goodFoundPairs[0] = newPairs;
      System.out.println(" Added " + numNewPairs + " newly found good pairs"); 
    }

    if (newAllFoundSet.size() > 0) {
      int numOldPairs = allFoundPairs[0].length;
      int numNewPairs = newAllFoundSet.size();
      int[] newPairs = new int[numOldPairs + numNewPairs];
      System.arraycopy(allFoundPairs[0], 0, newPairs, 0, numOldPairs);

      Iterator<Integer> iter = newAllFoundSet.iterator();
      int j = numOldPairs; 
      while (iter.hasNext()) { 
        newPairs[j++] = iter.next();
      }
      
      Arrays.sort(newPairs);
      allFoundPairs[0] = newPairs;
      System.out.println(" Added " + numNewPairs + " newly found total pairs"); 
    }
  }


  /** Generate pairs based on the instance indices.
   * Hopefully will be inlined by the compiler. */
  private final static int genPairCode(int idx1, int idx2) {
    if (idx1 > idx2) {
      return idx1 * (idx1 - 1)/2 + idx2;
    } else {
      return idx2 * (idx2 - 1)/2 + idx1;
    }
  }

  /** Calculate the combined cover of two blockers */
  public static double getCombinedCover(BlockingMap map1,
                                        BlockingMap map2,
                                        int[] goodPairs) {
    Object[][] instanceBlocks1 = map1.getInstanceBlocks();
    Object[][] instanceBlocks2 = map2.getInstanceBlocks();
    int numInstances = instanceBlocks1.length;
    
    HashMap<String, int[]> combinedBlockMap = new HashMap<String, int[]>();

    // create the new block map
    int numNewPairs = 0; 
    for (int i = 0; i < numInstances; i++) {
      Object[] blocks1 = instanceBlocks1[i];
      Object[] blocks2 = instanceBlocks2[i];

      for (int blockIdx1 = 0; blockIdx1 < blocks1.length; blockIdx1++) {
        for (int blockIdx2 = 0; blockIdx2 < blocks2.length; blockIdx2++) {
          String blockValue = blocks1[blockIdx1].toString() + "___"
            + blocks2[blockIdx2].toString();

          int [] instanceIndeces = combinedBlockMap.get(blockValue);
          if (instanceIndeces != null) { // seen this blockValue before
            int[] newInstanceIndeces = new int[instanceIndeces.length+1];
            System.arraycopy(instanceIndeces, 0, newInstanceIndeces, 0,
                             instanceIndeces.length);
            newInstanceIndeces[instanceIndeces.length] = i;
            instanceIndeces = newInstanceIndeces;

            numNewPairs = numNewPairs + instanceIndeces.length - 1; 
          } else {   // new blockValue
            instanceIndeces = new int[1];
            instanceIndeces[0] = i;
          }
          combinedBlockMap.put(blockValue, instanceIndeces); 
        }
      }
    }

    
    HashSet<Integer> foundPairs = new HashSet<Integer>(); 
    // go through the block map and check the number of positives and negatives
    for (int[] blockIndeces : combinedBlockMap.values()) {
      int numBlockInstances = blockIndeces.length;
      
      for (int i = 0; i < numBlockInstances - 1; i++) {
        int idx1 = blockIndeces[i];

        for (int j = i+1; j < numBlockInstances; j++) {
          int idx2 = blockIndeces[j];
          int pairCode = genPairCode(idx1, idx2);
          foundPairs.add(new Integer(pairCode)); 
        }
      } 
    }

    int numPositives = 0; 
    for (int pairIdx : goodPairs) {
      if (foundPairs.contains(pairIdx)) {
        numPositives++; 
      } 
    }

    int numNegatives = foundPairs.size() - numPositives;
    System.out.print("\tCover=" + ((double) numPositives / (foundPairs.size()+2))
                       + " (" + numPositives + "/" +foundPairs.size()+ ") for "); 
    return (numPositives + 0.0) / (numNegatives + 50.0);

  }

  public static BlockingMap getCombinedMap(BlockingMap map1,
                                           BlockingMap map2,
                                           boolean overlap,
                                           boolean redBlue) {
    return null; 
  }

}
