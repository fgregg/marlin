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
 *    SetCoverDNFBlockingLearner.java
 *    Copyright (C) 2005 Beena Kamath, Misha Bilenko
 *
 */


package weka.linkage.blocking.learn;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import weka.linkage.*;

/**
 * This class takes a set of blockers and learns the 
 * the best blocking criteria based on set cover algorithm.
 *
 * @author Beena Kamath, Misha Bilenko
 */

public class SetCoverDNFBlockingLearner extends SetCoverBlockingLearner
  implements OptionHandler, Serializable {

  /** Minimum amount of cover needed for DNF blockers */
  protected double m_minCover = 0.1;

  /** Disjunctions up to length k are considered */
  protected int m_K = 2; 

  /** Initialize the blocking learner.
   */
  public SetCoverDNFBlockingLearner() {
  }

  /** Given a list of blockers, add ComboBlockers
   * to the list corresponding to the disjunctions */
  public List<Blocker> getDisjunctions(List<Blocker> allBlockers, int k,
                                       Instances data, int[] goodPairs) {
    int numAttributes = data.numAttributes()-1; 
    int numBlockers = allBlockers.size();
    List<Blocker> dnfBlockers = new ArrayList<Blocker>();

    HashSet<Integer> blockerHash = new HashSet<Integer>();

    System.out.println("Good pairs size: " + goodPairs.length + "\n\n");


    for (int i = 0; i < numBlockers; i++) {
      Blocker firstBlocker = allBlockers.get(i);
      int[] blockerIdxs = new int[1];
      blockerIdxs[0] = i;

      int[] attrIdxs = new int[1];
      attrIdxs[0] = firstBlocker.attribute();

      if (data.attribute(firstBlocker.attribute()).name().equals("all")) {
        continue;
      }

      
      ComboBlocker comboBlocker = new ComboBlocker(blockerIdxs, allBlockers);
      BlockingMap comboMap = firstBlocker.getBlockingMap();

      List<ComboBlocker> comboBlockers = new ArrayList<ComboBlocker>(); 
      
      System.out.println("Building DNF starting with " + firstBlocker);

      { // TODO:  replace for k>3 while (blockerIdxs.length < k) {
        double[] bestCovers = new double[numAttributes];
        Arrays.fill(bestCovers, 0.0);
        
        int[] bestNextBlockerIdxs = new int[numAttributes];
        Arrays.fill(bestNextBlockerIdxs, -1); 

        int[] bestNextBlockerAttrIdxs = new int[numAttributes];
        Arrays.fill(bestNextBlockerAttrIdxs, -1); 
        
        for (int j = 0; j < numBlockers; j++) {
          // skip blockers that are already included
          if (Arrays.binarySearch(blockerIdxs, j) >= 0) continue;
      
          Blocker nextBlocker = allBlockers.get(j);
          int nextAttrIdx = nextBlocker.attribute();

          if (data.attribute(nextAttrIdx).name().equals("all")) {
            continue;
          }

          if (firstBlocker instanceof TFIDFBlocker
              && nextBlocker instanceof TFIDFBlocker) {
            continue;
          }
          

          // skip blockers for same attribute?
          if (Arrays.binarySearch(attrIdxs, nextAttrIdx) >= 0) {
            continue;
          }
          
          BlockingMap nextMap = nextBlocker.getBlockingMap();
          double cover = BlockingMap.getCombinedCover(comboMap, nextMap, goodPairs);

          System.out.println("\tConsidering " + nextBlocker +
                             " \tcombined cover: " + cover); 

          if (cover > bestCovers[nextAttrIdx]) {
            bestCovers[nextAttrIdx] = cover;
            bestNextBlockerIdxs[nextAttrIdx] = j;
            bestNextBlockerAttrIdxs[nextAttrIdx] = nextAttrIdx; 
          }
        }

        
        int toKeep = 2;
        for (int j = 0; j < numAttributes; j++) {
          if (bestNextBlockerIdxs[j] >= 0
              && bestCovers[j] > m_minCover) {
            int numBetterBlockers = 0; 
            for (int l = 0; l < numAttributes; l++) {
              if (bestCovers[l] > bestCovers[j]) {
                numBetterBlockers++;
              }
            }

            if (numBetterBlockers >= toKeep) {
              bestNextBlockerIdxs[j] = -1;
              System.out.println("best blocker for attribute " +
                                  data.attribute(j).name()
                                 + " not good enough"); 
            }
          }
        }

       

        // Add a new disjunction for each attribute under consideration
        for (int j = 0; j < numAttributes; j++) {
          if (bestNextBlockerIdxs[j] < 0) {
            System.out.println("No blockers found to extend " + comboBlocker
                               + " on attribute " + data.attribute(j).name());
          } else {
            if (bestCovers[j] > m_minCover) { 
              System.out.println("Combining " + comboBlocker +
                                 " and " + allBlockers.get(bestNextBlockerIdxs[j])
                                 + " cover=" + bestCovers[j]);

              int[] comboBlockerIdxs = appendIntArray(blockerIdxs,
                                                      bestNextBlockerIdxs[j]);
              Arrays.sort(comboBlockerIdxs);

              int[] newAttrIdxs = appendIntArray(attrIdxs, bestNextBlockerAttrIdxs[j]);
              Arrays.sort(newAttrIdxs);

              ComboBlocker newComboBlocker = new ComboBlocker(comboBlockerIdxs,
                                                              allBlockers);
              //            newComboBlocker.blockData(data, m_redBlue);
              comboBlockers.add(newComboBlocker); 
            } else {
              System.out.println("NOT Combining " + comboBlocker +
                                 " and " + allBlockers.get(bestNextBlockerIdxs[j])
                                 + "; insufficient cover=" + bestCovers[j]);
            }
          }
        }
      }


      // make sure we didn't add the same disjunction before
      for (ComboBlocker newComboBlocker : comboBlockers) { 
        int hashCode = 0;
        int power = 1;
        int[] idxs = newComboBlocker.m_idxs; 
        for (int j = 0; j < idxs.length; j++) {
          hashCode += idxs[j] * power;
          power *= numBlockers;
        }

        if (true) { //(!blockerHash.contains(hashCode) && blockerIdxs.length == k) {
          System.out.print("ADDING BLOCKER [ ");
          for (int idx : idxs) System.out.print(idx+" ");
          System.out.println("]:  " + newComboBlocker); 
          dnfBlockers.add(newComboBlocker);
          blockerHash.add(hashCode);
        } else {
          System.out.println("SKIPPING BLOCKER " + newComboBlocker);
        }
      }
    }
    return dnfBlockers; 
  } 

   /** Given a set of records and the feature to be blocked,
   *   build the vector space
   */
  public ArrayList getBlockers(Blocker[] blockers, Instances data) {

    List<Blocker> unaryBlockers = createAllBlockerList(blockers, data);

    for (Blocker blocker : unaryBlockers) { 
      blocker.blockData(data, m_redBlue);
    }

    int[] goodPairs = getRealPairs(data);

    List<Blocker> allBlockers = new ArrayList<Blocker>();
    allBlockers.addAll(unaryBlockers); 
    
    for (int k = 2; k <= m_K; k++) { 
      List<Blocker> dnfBlockers = getDisjunctions(unaryBlockers,k,data,goodPairs);
      for (Blocker blocker : dnfBlockers) {
        blocker.blockData(data, m_redBlue);
      }
      allBlockers.addAll(dnfBlockers); 
    }

    return getBlockersSetCover(allBlockers, goodPairs);
  }


  /** Set/get K */
  public void setK(int k) { m_K = k; }
  public int getK() { return m_K; }

  /** Set/get minCover */
  public void setMinCover(double min) { m_minCover = min; }
  public double getMinCover() { return m_minCover; }

  /** append an int array */
  public static int[] appendIntArray(int[] array, int value) {
    int[] newArray = new int[array.length+1];
    System.arraycopy(array, 0, newArray, 0, array.length);
    newArray[array.length] = value;
    return newArray;
  }
  
  /**
   * Gets the current settings of the blocker.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String[] parentOptions = super.getOptions(); 
    String [] options = new String [parentOptions.length+2];

    int current = 0;
    while (parentOptions[current].length() > 0) {
      options[current] = parentOptions[current++];
    }

    options[current++] = "-K" + m_K; 

    if (m_minCover > 0) {
      options[current++] = "-minCover" + m_minCover; 
    }

    for (int i = current; i < options.length; i++)  options[current++] = ""; 
    return options;
  }

}
