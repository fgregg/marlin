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
 *    SetCoverBlockingLearner.java
 *    Copyright (C) 2005 Beena Kamath
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
 * @author Beena Kamath
 */

public class SetCoverBlockingLearner extends BlockingLearner
  implements OptionHandler, Serializable {

  /** If memory is tight, don't track negative covered pairs */
  protected boolean m_trackNegatives = false;

  /** Maximum number of blockers desired.   */
  protected int m_maxBlockers = Integer.MAX_VALUE;

  /** Minimum recall improvement required on every iteration
   * (early stopping; set to 0 if not wanted) */
  protected double m_minImprovement = 1e-3;
  protected double m_minRecall = 1.0;

  /** Number of negatives we can lose */
  protected int m_epsilon = 10; 

  /** Use RedBlueEstimation?? */
  protected boolean m_redBlue = true;

  /** Threshold on the number of negatives that any blocker considered may return */
  protected int m_eta = Integer.MAX_VALUE; 

  /** Initialize the blocking learner.
   */
  public SetCoverBlockingLearner() {
  }

  /** Given "base" blockers, create a list of these blockers for every
   *  attribute of the supplied dataset */
  protected List<Blocker> createAllBlockerList(Blocker[] blockers, Instances data){
    List<Blocker> allBlockers = new ArrayList<Blocker> ();
    int classIdx = data.classIndex();
    for (int i = 0; i < data.numAttributes(); i++) {
      
      if (i != classIdx) {
        for (int j = 0; j < blockers.length; j++) {
          Blocker blocker = blockers[j];
          
          if (((blocker.type()  == Attribute.NUMERIC) ||
               (data.attribute(i).type() == Attribute.NUMERIC)) &&
              (blocker.type() != data.attribute(i).type())) {
            continue;
          }

          if (blocker instanceof TFIDFBlocker
              && ((TFIDFBlocker)blocker).getTokenizer().getClass().getName().contains("NGram")
              && data.attribute(i).name().equals("all"))
            continue;
          
          Blocker newBlocker = (Blocker)blocker.clone();
          newBlocker.setAttribute(i); 
          allBlockers.add(newBlocker);
        }
      }
    }
    return allBlockers; 
  }

  /** Given a set of records and the feature to be blocked,
   *   build the vector space   */
  public ArrayList getBlockers(Blocker[] blockers, Instances data) {

    List<Blocker> allBlockers = createAllBlockerList(blockers, data);
    for (Blocker blocker : allBlockers) { 
      blocker.blockData(data, m_redBlue);
    }

    int[] goodPairs = getRealPairs(data);
    System.out.println("looking for " + goodPairs.length + " good pairs");
    
    // Throwing out blockers that are too horrible early (Peleg's algorithm):
    if (m_eta < Integer.MAX_VALUE) {
      filterBlockers(allBlockers, goodPairs);
      System.out.println("\n\nDone filtering; left with " +
                         allBlockers.size() + " blockers\n\n\n"); 
    }

    return getBlockersSetCover(allBlockers, goodPairs);
  }

  
  /** Given a set of blockers, throw out those that cover too many negatives */
  protected void filterBlockers(List<Blocker> allBlockers, int[] goodPairs) {
    System.out.println("\n\n*** Filtering blockers, eta=" + m_eta);

    int numGoodPairs = goodPairs.length;
    ArrayList<Blocker> uselessBlockerList = new ArrayList<Blocker>();
    
    for (int i = 0; i < allBlockers.size(); i++) { 
      Blocker blocker = (Blocker) allBlockers.get(i);
      double cover = blocker.getCoverEstimate(goodPairs,
                                              new int[0], new int[0]);

      int numBadPairs = 0;
//       if (blocker instanceof TFIDFBlocker) {
//         numBadPairs = blocker.getPairs().size() -  blocker.getGoodPairs().size();
//       } else {
      {  numBadPairs = (int)( blocker.m_blockingMap.m_goodBlockedPairs.length
        / cover);
      } 

      System.out.print(" \t" + i + "." + blocker + "\t cover=" + cover +
                       " negatives covered=" +
                       numBadPairs + "\t"); 
      if (numBadPairs > m_eta) {
        uselessBlockerList.add(blocker);
        System.out.println("REMOVING!") ;
      } else {
        System.out.println("KEEPING!") ;
      }
    }

    for (Blocker uselessBlocker : uselessBlockerList) { 
      allBlockers.remove(uselessBlocker);
    }
    System.out.println("Removed " + uselessBlockerList.size() +
                       "; left with " + allBlockers.size());
  }


  
  /** Given a set of blockers, use set cover algorithm to return an optimal subset */
  protected ArrayList getBlockersSetCover(List<Blocker> allBlockers,
                                          int[] goodPairs) {
    ArrayList bestBlockers = new ArrayList();
    int numGoodPairs = goodPairs.length;
    
    int[][] goodFoundPairs = new int[1][0];

    
    // iterate until cover all pairs or until out of blockers or until improvement too small
    double lastImprovement = 1.0;
    double recall = 0;
    double maxCost = 0; 
    while (goodFoundPairs[0].length < numGoodPairs - m_epsilon 
           && allBlockers.size() > 0 
           && bestBlockers.size() < m_maxBlockers
           //  && lastImprovement >= m_minImprovement
           && recall < m_minRecall) {
      double bestCover = 0.0;
      Blocker bestBlocker = null;
      int bestIdx = -1;
      ArrayList<Blocker> uselessBlockerList = new ArrayList<Blocker>();

      // find best blocker among blockers
      int i = 0; 
      for (Blocker blocker : allBlockers) {
        System.out.print(" " + i + ". " + blocker + '\t'); 
        
	double cover = blocker.getCoverEstimate(goodPairs,
                                                goodFoundPairs[0], null);
        System.out.println("\tcover=" + (float)cover); 

        if (cover > bestCover && cover > m_minImprovement) {
          bestCover = cover;
          bestBlocker = blocker;
          bestIdx = i; 
        }
        if (cover == 0) {
          uselessBlockerList.add(blocker);
        }
        i++;
      }

      if (bestBlocker == null) {
        System.out.println("\n\nRAN OUT OF BLOCKERS!\nRAN OUT OF BLOCKERS!\n");
        break;
      }

      int oldGoodFoundPairs = goodFoundPairs[0].length;
      bestBlocker.updatePairLists(goodPairs, goodFoundPairs, null);
//       if (!(bestBlocker instanceof ComboBlocker ||
//             bestBlocker instanceof TFIDFBlocker)) { 
//         bestBlocker.resetBlocker();
//       }
      bestBlockers.add(bestBlocker);
      allBlockers.remove(bestBlocker);   

      for (Blocker uselessBlocker : uselessBlockerList) { 
        allBlockers.remove(uselessBlocker);
      } 

      lastImprovement = (goodFoundPairs[0].length - oldGoodFoundPairs + 0.0)
        / numGoodPairs;
      recall = (goodFoundPairs[0].length+0.0)/numGoodPairs;
       
      System.out.println("\n" + bestBlockers.size() + ".  After adding " +
                         bestIdx + ": "
                         + bestBlocker + " (" + (float)bestCover + ") found " 
                         + goodFoundPairs[0].length +  "/" + numGoodPairs +
                         "(" + (float)recall + ") "
                         + "; %improvement: " + (float)lastImprovement + "\n"); 
    }

//     Instances instances = ((Blocker)bestBlockers.get(0)).m_instances;
//     for (int i = 0; i < goodPairs.length; i++) {
//       if (Arrays.binarySearch(goodFoundPairs[0], goodPairs[i]) < 0) {
//         int pairIdx = goodPairs[i];
//         int idx1 = 1+(int)Math.floor((-1.0+Math.sqrt(1.0+8.0*pairIdx))/2.0);
//         int idx2 = pairIdx - idx1*(idx1-1)/2;

//         System.out.println("missed pair: " +
//                            instances.instance(idx1));
//         System.out.println("\t" +
//                            instances.instance(idx2));
//       }
//     }

    filterSubsumedBlockers(bestBlockers); 

    for (int i = 0; i < allBlockers.size(); i++) {
      Blocker blocker = allBlockers.get(i);
      blocker.resetBlocker();
    }

    return bestBlockers; 
  }

  /* Check if any of the later blockers subsumes an earlier blocker */
  protected void filterSubsumedBlockers(List blockerList) { 
    List<Blocker> subsumedBlockerList = new ArrayList<Blocker>();
    for (int i = 0; i < blockerList.size()-1; i++) {
      Blocker earlyBlocker = (Blocker) blockerList.get(i);
      HashSet<Integer> earlyGoodPairs = earlyBlocker.getGoodPairs();

      boolean subsumes = true;
      for (int j = i+1; j < blockerList.size(); j++) {
        Blocker lateBlocker =  (Blocker) blockerList.get(j);
        HashSet<Integer> lateGoodPairs = lateBlocker.getGoodPairs();
        for (Integer earlyGoodPair : earlyGoodPairs) {
          if (!lateGoodPairs.contains(earlyGoodPair)) {
            subsumes = false;
            break;
          }
        }

        if (subsumes) {          
          System.out.println(earlyBlocker + "(" + earlyGoodPairs.size()
                             + ") is subsumed by " + lateBlocker
                             + "(" + lateGoodPairs.size() + ")");
          subsumedBlockerList.add(earlyBlocker);
          continue;
        } 
      }
    }

    for (Blocker earlyBlocker : subsumedBlockerList) { 
      blockerList.remove(earlyBlocker);
    }
    System.out.println("Removed " + subsumedBlockerList.size() +
                       "; left with " + blockerList.size() + "\n\n\n");
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


  /** Set/get the tracknegatives */
  public void setTrackNegatives(boolean b) { m_trackNegatives = b; }
  public boolean getTrackNegatives() { return m_trackNegatives; }

  /** Set/get the minimum recall improvement */
  public void setMinImprovement(double d) { m_minImprovement = d; }
  public double getMinImprovement() { return m_minImprovement; }

  /** Set/get the minimum recall required */
  public void setMinRecall(double r) { m_minRecall = r; }
  public double getMinRecall() { return m_minRecall; }
  
  /** Set/get the maxBlockers */
  public void setMaxBlockers(int b) { m_maxBlockers = b; }
  public int getMaxBlockers() { return m_maxBlockers; }

  /** Set/get the eta */
  public void setEta(int eta) { m_eta = eta; }
  public int getEta() { return m_eta; }

  /** Set/get the epsilon */
  public void setEpsilon(int epsilon) { m_epsilon = epsilon; }
  public int getEpsilon() { return m_epsilon; }
  
  /** Set/get estimation technique */
  public void setRedBlueEstimation(boolean r) { m_redBlue = r; }
  public boolean getRedBlueEstimation() { return m_redBlue; }

  /**
   * Gets the current settings of the blocker.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [10];

    int current = 0;
    if (m_trackNegatives) {
      options[current++] = "-trackNeg";
    }

    if (m_redBlue) {
      options[current++] = "-redBlue";
      if (m_eta < Integer.MAX_VALUE) {
        options[current++] = "-eta" + m_eta;
      }
    }

    if (m_epsilon > 0) {
      options[current++] = "-eps" + m_epsilon; 
    }

    if (m_minRecall < 1) {
      options[current++] = "-minRec" + m_minRecall; 
    }

    if (m_minImprovement > 0) {
      options[current++] = "-minImprv" + m_minImprovement; 
    }

    if (m_maxBlockers < Integer.MAX_VALUE) 
    options[current++] = "-maxBlockers" + m_maxBlockers;

    for (int i = current; i < options.length; i++)  options[current++] = ""; 
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
