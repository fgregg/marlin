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
 *    TFIDFBlocker.java
 *    Copyright (C) 2006 Misha Bilenko
 *
 */


package weka.linkage.blocking.learn;

import java.util.*;
import java.io.Serializable;

import java.text.SimpleDateFormat;
import java.text.DecimalFormat;

import weka.core.*;
import weka.linkage.*;
import weka.linkage.blocking.*;
import weka.linkage.metrics.Tokenizer;
import weka.linkage.metrics.WordTokenizer;
import weka.linkage.metrics.NGramTokenizer;
import weka.linkage.metrics.TokenInfo;
import weka.linkage.metrics.HashMapVector;
import weka.linkage.metrics.Weight;



/**
 * Create blocks using the "Canopies" algorithm (McCallum, Ungar, Nigam, KDD-2000)
 * 
 * @author Misha Bilenko
 */

public class TFIDFBlocker extends Blocker
  implements OptionHandler, Serializable {

  /** Instances are mapped to StringReferences in this hash */
  //  protected HashMap<Integer,StringRef> m_strRefMap = null;
  
  /** A HashMap where tokens are indexed. Each indexed token maps to a TokenInfo. */
  protected HashMap<String,TokenInfo> m_tokenInfoMap = null;

  /** A list of all indexed instance.  Elements are InstanceReference's. */
  public ArrayList<StringRef> m_strRefList = null;

  /** An underlying tokenizer used to convert strings into HashMapVectors  */
  protected Tokenizer m_tokenizer = new WordTokenizer();

  /** Should IDF weighting be used? */
  protected boolean m_useIDF = true;

  /** Minimum TF-IDF threshold required */
  protected double m_simThreshold = 0.6; 
 
  /** Initializations 
   */
  public TFIDFBlocker() {
    resetBlocker();
  }

  /** Free up unnecessary memory */
  public void resetBlocker() {
    m_instances = null; 
    m_blockingMap = null;
    m_tokenInfoMap = null;
    m_strRefList = null;
  }

  /** Return a copy of this Blocker */
  public Blocker clone() {
    TFIDFBlocker blocker = new TFIDFBlocker();
    blocker.setUseIDF(m_useIDF);
    blocker.setSimThreshold(m_simThreshold);
    blocker.setTokenizer(m_tokenizer); 
    return blocker;
  }

  /** Blocks are overlapping (each instance belongs to multiple blocks) */
  public boolean blocksOverlap() {
    return true;
  }

  /** Given the data, construct the blocking */
  public void blockData(Instances instances, boolean redBlue) {
    if (m_instances != null) {
      return; 
    } 
    m_instances = instances;
    int numInstances = instances.numInstances();

    m_tokenInfoMap = new HashMap<String,TokenInfo>();
    m_strRefList = new ArrayList<StringRef>(numInstances);
    for (int i = 0; i < numInstances; i++) { 
      Instance instance = instances.instance(i);
      String val = instance.stringValue(m_attrId); 
      HashMapVector vector = m_tokenizer.tokenize(val);
      vector.initLength();
      indexInstance(i, val, vector);
    }

    // Now that all instances have been processed, we can calculate the IDF weights for
    // all tokens and the resulting lengths of all weighted document vectors.
    computeIDFandStringLengths();
    System.out.print('\t' + getTimestamp() + " " + toString() + "\t" 
                       + m_strRefList.size() + " strings, "
                       + m_tokenInfoMap.size() + " tokens; ");
    createPairSet();
    System.out.println(getTimestamp() + " Done creating the pair set "); 
  }


    /** Index a given Instance using its corresponding vector */
  protected void indexInstance(int instanceIdx, String str, HashMapVector vector) {
    // Create a new reference
    StringRef strRef = new StringRef(instanceIdx,
                                     m_instances.instance(instanceIdx).classValue(),
                                     str, vector);
    m_strRefList.add(strRef);
    
    //    m_strRefMap.put(instanceIdx, strRef);
    // Iterate through each of the tokens in the document
    Iterator mapEntries = vector.iterator();
    while (mapEntries.hasNext()) {
      Map.Entry entry = (Map.Entry)mapEntries.next();
      // An entry in the HashMap maps a token to a Weight
      String token = (String)entry.getKey();
      // The count for the token is in the value of the Weight
      int count = (int)((Weight)entry.getValue()).getValue();
      // Add an occurence of this token to the inverted index pointing to this document
      indexToken(token, count, strRef);
    }
  }

  
  /** Add a token occurrence to the index.
   * @param token The token to index.
   * @param count The number of times it occurs in the document.
   * @param instRef A reference to the Instance it occurs in.
   */
  protected void indexToken(String token, int count, StringRef strRef) {
    // Find this token in the index
    TokenInfo tokenInfo = m_tokenInfoMap.get(token);
    if (tokenInfo == null) {
      // If this is a new token, create info for it to put in the hashtable
      tokenInfo = new TokenInfo();
      m_tokenInfoMap.put(token, tokenInfo);
    }
    // Add a new occurrence for this token to its info
    tokenInfo.occList.add(new TokenStrOccurrence(strRef, count));
  }

  
  /** Compute the IDF factor for every token in the index and the length
   * of the string vector for every string referenced in the index. */
  protected void computeIDFandStringLengths() {
    // Let N be the total number of documents indexed
    double N = m_strRefList.size();
    // Iterate through each of the tokens in the index 
    Iterator mapEntries = m_tokenInfoMap.entrySet().iterator();
    while (mapEntries.hasNext()) {
      // Get the token and the tokenInfo for each entry in the HashMap
      Map.Entry entry = (Map.Entry)mapEntries.next();
      String token = (String)entry.getKey();
      TokenInfo tokenInfo = (TokenInfo)entry.getValue();

      // Get the total number of strings in which this token occurs
      double numStringRefs = tokenInfo.occList.size(); 

      // Calculate the IDF factor for this token
      double idf = Math.log(N/numStringRefs);
      if (idf == 0.0) 
	// If IDF is 0, then just remove this "all-strings" token from the index
	mapEntries.remove();
      else {
	tokenInfo.idf = idf;
	// In order to compute document vector lengths,  sum the
	// square of the weights (IDF * occurrence count) across
	// every token occurrence for each document.
	for(int i = 0; i < tokenInfo.occList.size(); i++) {
	  TokenStrOccurrence occ =
            (TokenStrOccurrence)tokenInfo.occList.get(i);
	  if (m_useIDF) { 
	    occ.stringRef.length = occ.stringRef.length +
              (idf*occ.count) * (idf*occ.count);
	  } else {
	    occ.stringRef.length = occ.stringRef.length +
              occ.count * occ.count;
	  }
	}
      }
    }
    // At this point, every document length should be the sum of the squares of
    // its token weights.  In order to calculate final lengths, just need to
    // set the length of every document reference to the square-root of this sum.
    for(StringRef strRef : m_strRefList) { 
      strRef.length = Math.sqrt(strRef.length);
    }
  }


  
  /** Populate m_pairSet with all the pairs that contain common tokens, so that
   * they can be retrieved in the order of decreasing similarity later */
  public void createPairSet() {
    int numInstances = m_instances.numInstances();
    HashSet<Integer> centers = new HashSet<Integer>();
    for (int i = 0; i < numInstances; i++) {
      centers.add(i);
    }
    int numCenters = 0; 

    m_blockingMap = new BlockingMap(m_instances.numInstances(),
                                    true, true);    

    while (centers.size() > 0) {

      // get a random center
      Iterator<Integer> iter = centers.iterator();
      int centerIdx = iter.next();
      StringRef centerStrRef = m_strRefList.get(centerIdx); 

      // initialize the canopy with the center
      HashSet<Integer> canopy = new HashSet<Integer>();
      canopy.add(centerIdx);

      // avoid computing similarity twice
      HashSet<Integer> seenInstances = new HashSet<Integer>();

      // iterate through all tokens in the center
      Set tokens = centerStrRef.vector.hashMap.keySet();
      Iterator tokenIter = tokens.iterator();
      while (tokenIter.hasNext()) {
        String token = (String) tokenIter.next();
        TokenInfo tokenInfo = m_tokenInfoMap.get(token);
        
        // Get the total number of strings in which this token occurs
        int numStringRefs = tokenInfo.occList.size();

        // if more than 1, compare pair and add to the index
        if (numStringRefs > 1 && numStringRefs < 1000) {
          
          for (int i = 0; i < numStringRefs; i++) {
            StringRef stringRef =
              ((TokenStrOccurrence) tokenInfo.occList.get(i)).stringRef;
            int idx = stringRef.idx;

            if (idx != centerIdx && !seenInstances.contains(idx)) {
              seenInstances.add(idx);

              double sim = similarity(centerStrRef, stringRef);
              if (sim >= m_simThreshold) {
                canopy.add(idx);

                // remove from centers
                centers.remove(idx); 
              }
            }
          }
        }
      }

      // remove from centers
      centers.remove(centerIdx);
      ++numCenters; 

      if (canopy.size() > 1) {
        for (int idx : canopy) {
          m_blockingMap.putInstanceIdx(idx, centerIdx);
          m_blockingMap.addInstanceBlock(idx, centerIdx);
        } 
      } 
    }

    System.out.println(numCenters + " centers.");
    // + numNonCenters + " non-centers"); 
  }

  

  /** Compute similarity between two strings
   * @param s1 first string
   * @param s2 second string
   * @returns similarity between two strings
   */
  public double similarity(StringRef stringRef1, StringRef stringRef2) {
    double length1 = stringRef1.length;
    double length2 = stringRef2.length;
    HashMapVector v1 = stringRef1.vector;
    HashMapVector v2 = stringRef2.vector;
    double similarity = 0;
        
    if (length1 == 0 || length2 == 0) {
      return 0;
    }

    Iterator mapEntries = v1.iterator();
    while (mapEntries.hasNext()) {
      // Get the token and the count for each token in the query
      Map.Entry entry = (Map.Entry)mapEntries.next();
      String token = (String)entry.getKey();
      if (v2.hashMap.containsKey(token)) {
        double count1 = ((Weight)entry.getValue()).getValue();
        double count2 = ((Weight)v2.hashMap.get(token)).getValue();
        TokenInfo tokenInfo = m_tokenInfoMap.get(token);

        // add this component unless it was killed (with idf=0)
        if (tokenInfo != null) {
          double increment = count1 * count2;
          if (m_useIDF) {
            increment *= tokenInfo.idf * tokenInfo.idf;
          }
          similarity += increment;
        }
      }
    }
    similarity /= length1 * length2;
    return similarity;
  }

  
  
  /** Return the list of blocks for a given instance */
  public Object[] getBlocks (Instance instance) {
    int idx = (int) instance.weight();
//     if (m_blockingMap == null || m_blockingMap.m_instanceBlocks==null) { 
//       System.out.println("\n\nWHAAAAAT?\n\n");
//       System.out.println(toString());
//       System.out.println(m_blockingMap.m_instanceBlocks);
//       return null;
//     } 
    return m_blockingMap.m_instanceBlocks[idx]; 
  } 



  
  /** Given two instances, do they fall into the same block? */
  public boolean sameBlock(Instance instance1, Instance instance2) {
    String str1 = instance1.stringValue(m_attrId);
    HashMapVector vector1 = m_tokenizer.tokenize(str1);
    vector1.initLength();
    StringRef strRef1 = new StringRef((int)instance1.weight(),
                                      instance1.classValue(),
                                      str1, vector1);

    String str2 = instance2.stringValue(m_attrId);
    HashMapVector vector2 = m_tokenizer.tokenize(str2);
    vector2.initLength();
    StringRef strRef2 = new StringRef((int)instance2.weight(),
                                      instance2.classValue(),
                                      str2, vector2);

    if (similarity(strRef1, strRef2) >= m_simThreshold) {
      return true;
    } else {
      return false;
    }
  }

  

   /** Turn IDF weighting on/off
   * @param useIDF if true, all token weights will be weighted by IDF
   */
  public void setUseIDF(boolean useIDF) { m_useIDF = useIDF;  } 
  public boolean getUseIDF() {  return m_useIDF; } 


  /** Set/get the similarity threshold */
  public void setSimThreshold(double threshold) { m_simThreshold = threshold; }
  public double getSimThreshold() { return m_simThreshold; }

  /** Set/get the tokenizer */
  public void setTokenizer(Tokenizer tokenizer) { m_tokenizer = tokenizer; } 
  public Tokenizer getTokenizer() { return m_tokenizer; } 


  /**
   * Gets the current settings of the blocker.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [10];
    int current = 0;
    
    options[current++] = "-minSim" + m_simThreshold;

    if (m_useIDF == false) {
      options[current++] = "-noIDF";
    }

    String tokenizerName =  Utils.removeSubstring(m_tokenizer.getClass().getName(),
                                                  "weka.linkage.metrics.");
    tokenizerName =  Utils.removeSubstring(tokenizerName, "Tokenizer");
    options[current++] = "-T-" + tokenizerName;
    if (m_tokenizer instanceof OptionHandler) {
	String[] tokenizerOptions = ((OptionHandler)m_tokenizer).getOptions();
	for (int i = 0; i < tokenizerOptions.length
               && tokenizerOptions[i].length()>0; i++) {
	  options[current++] = tokenizerOptions[i];
	}
      }

    while (current < options.length) {
      options[current++] = "";
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
    String tokenizerName =  Utils.removeSubstring(m_tokenizer.getClass().getName(),
                                                  "weka.linkage.metrics.");
    tokenizerName =  Utils.removeSubstring(tokenizerName, "Tokenizer");

    if (m_tokenizer instanceof NGramTokenizer) {
      tokenizerName = tokenizerName + ((NGramTokenizer)m_tokenizer).getN();
    }
    
    return Utils.removeSubstring(this.getClass().getName(),
                                 "weka.linkage.blocking.learn.")
      + "-" +
      (m_instances == null ? m_attrId : m_instances.attribute(m_attrId).name())
      + "-" + tokenizerName + "-" + m_simThreshold;
  } 

   /**
   * Gets a string containing current date and time.
   *
   * @return a string containing the date and time.
   */
  protected static String getTimestamp() {
    return (new SimpleDateFormat("HH:mm:ss:")).format(new Date());
  }

}
