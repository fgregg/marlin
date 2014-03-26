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
 *    NonLearnableBlocking.java
 *    Copyright (C) 2003 Mikhail Bilenko
 *
 */


package weka.linkage.blocking;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import java.text.SimpleDateFormat;

import weka.linkage.metrics.*;
import weka.linkage.*;

/**
 * This class takes a set of records, amalgamates them into single
 * strings and creates an inverted index for that collection.  It then
 * can return the pairs of strings that are most alike.  Largely
 * borrowed from VectorSpaceMetric.
 *
 * @author Mikhail Bilenko
 */
public class NonLearnableBlocking extends Blocking implements OptionHandler, Serializable {
  /** The dataset that contains the instances */ 
  protected Instances m_instances = null;
  
  /** Strings are mapped to StringReferences in this hash */
  protected HashMap m_instanceRefHash = null;

  /** A HashMap where tokens are indexed. Each indexed token maps
   * to a TokenInfo. */
  protected HashMap m_tokenHash = null;

  /** A TreeSet where the InstancePairs are stored for subsequent retrieval */
  protected TreeSet m_pairSet = null;


  /** A list of all indexed instance.  Elements are InstanceReference's. */
  public ArrayList m_instanceRefs = null;

  /** An underlying tokenizer used to convert strings into HashMapVectors  */
  protected Tokenizer m_tokenizer = new WordTokenizer();

  /** Should IDF weighting be used? */
  protected boolean m_useIDF = true;

  /** Minimum TF-IDF threshold required */
  protected double m_simThreshold = 0.1; 

  /** Construct a vector space from a given set of examples
   * @param strings a list of strings from which the inverted index is
   * to be constructed
   */
  public NonLearnableBlocking() {
    m_pairSet = new TreeSet(new InstancePairComparator());
    m_instanceRefHash = new HashMap();
    m_tokenHash = new HashMap();
    m_instanceRefs = new ArrayList();
  }


  /** Do nothing */
  public void learnBlocking(Instances data) {
    m_statistics = new ArrayList(); 
  }


   /** Given a list of strings, build the vector space
   */
  public void buildIndex(Instances instances) throws Exception {
    m_instances = instances; 
    m_pairSet = new TreeSet(new InstancePairComparator());
    m_instanceRefHash = new HashMap();
    m_tokenHash = new HashMap();
    int classIndex = instances.classIndex();

    for (int i = 0; i < instances.numInstances(); i++) { 
      Instance instance = instances.instance(i);
      StringBuffer buffer = new StringBuffer();
      for (int j = 0; j < instance.numAttributes(); j++) {
	if (j != classIndex) {
	  buffer.append(instance.stringValue(j)).append(" ");
	} 
      }
      // Create a document vector for this document
      String string = buffer.toString();
      HashMapVector vector = m_tokenizer.tokenize(string);
      vector.initLength();
      indexInstance(instance, i, string, vector);
    }
    // Now that all instances have been processed, we can calculate the IDF weights for
    // all tokens and the resulting lengths of all weighted document vectors.
    computeIDFandStringLengths();
    System.out.println(getTimestamp() + " Indexed " +  m_instanceRefs.size() + " documents with " + size() + " unique terms.");
    createPairSet();
    System.out.println(getTimestamp() + " Created a set with " + m_pairSet.size() + " pairs");

    if (m_statistics != null) { // we are doing this just for blocking evaluation
      // COUNT THE NUM TRUE PAIRS
      int numTruePairs = 0;
      // get the class counts
      HashMap classCountMap = new HashMap();
      
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
      Iterator iterator = classCountMap.values().iterator();
      while (iterator.hasNext()) {
        int counts = ((Integer) iterator.next()).intValue();
        numTruePairs += counts * (counts - 1) / 2;
      }
      // END COUNT NUM TRUE PAIRS

      int m_numTotalPairs = 0;
      int m_numGoodPairs = 0;
      int m_numTruePairs = numTruePairs;
      int m_numTotalPairsTest = instances.numInstances() * (instances.numInstances() -1)/2;
      int m_numTotalPairsTrain = 0;
      int m_numPotentialDupePairsTrain = 0;
      int m_numActualDupePairsTrain = 0;
      int m_numPotentialNonDupePairsTrain = 0; 
      int m_numActualNonDupePairsTrain = 0; 


      InstancePair[] pairs = getMostSimilarPairs(m_numTotalPairsTest);
      

      for (int i = 0; i < pairs.length; i++) {
        Object[] currentStats = new Object[21];

        InstancePair pair = pairs[i];
        if (pair.instance1.classValue() == pair.instance2.classValue()) { 
          m_numGoodPairs++;
        }
        m_numTotalPairs++;
      }

      
        
      double precision = m_numTotalPairs>0 ? (m_numGoodPairs+0.0)/m_numTotalPairs : 0.0; 
      double recall = (m_numGoodPairs+0.0)/m_numTruePairs;
      double speedup = 1.0 - m_numTotalPairs/(m_numTotalPairsTest+0.0);
      
      double fmeasure = 0;
      
      if (precision + recall > 0) {  // avoid divide by zero in the p=0&r=0 case
        fmeasure = 2 * (precision * recall) / (precision + recall);
      }
      
      System.out.println("\tBlocking stats:  P=" +(float)precision + "\tR=" + (float)recall +
                         "\t" + m_numGoodPairs + "("
                         + m_numTruePairs + ")/" + m_numTotalPairs + "\tFM=" + fmeasure);

      Object[] currentStats = new Object[21];
      int statIdx = 0;
      currentStats[statIdx++] = new Double(instances.numInstances());

      // Accuracy statistics          
      currentStats[statIdx++] = new Double(recall);
      currentStats[statIdx++] = new Double(precision);
      currentStats[statIdx++] = new Double(speedup);         
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
      currentStats[statIdx++] = new Double(0);

      // Timing statistics
      currentStats[statIdx++] = new Double(0);
      currentStats[statIdx++] = new Double(0);
      
      m_statistics.add(currentStats);
    }
  }

  /** Index a given Instance using its corresponding vector */
  protected void indexInstance(Instance instance, int idx, String string, HashMapVector vector) {
    // Create a new reference
    InstanceReference instRef = new InstanceReference(instance, idx, string, vector);
    m_instanceRefs.add(instRef);
    
    m_instanceRefHash.put(instance, instRef);
    // Iterate through each of the tokens in the document
    Iterator mapEntries = vector.iterator();
    while (mapEntries.hasNext()) {
      Map.Entry entry = (Map.Entry)mapEntries.next();
      // An entry in the HashMap maps a token to a Weight
      String token = (String)entry.getKey();
      // The count for the token is in the value of the Weight
      int count = (int)((Weight)entry.getValue()).getValue();
      // Add an occurence of this token to the inverted index pointing to this document
      indexToken(token, count, instRef);
    }
  }

  /** Add a token occurrence to the index.
   * @param token The token to index.
   * @param count The number of times it occurs in the document.
   * @param instRef A reference to the Instance it occurs in.
   */
  protected void indexToken(String token, int count, InstanceReference instRef) {
    // Find this token in the index
    TokenInfo tokenInfo = (TokenInfo)m_tokenHash.get(token);
    if (tokenInfo == null) {
      // If this is a new token, create info for it to put in the hashtable
      tokenInfo = new TokenInfo();
      m_tokenHash.put(token, tokenInfo);
    }
    // Add a new occurrence for this token to its info
    tokenInfo.occList.add(new TokenInstanceOccurrence(instRef, count));
  }

  /** Compute the IDF factor for every token in the index and the length
   * of the string vector for every string referenced in the index. */
  protected void computeIDFandStringLengths() {
    // Let N be the total number of documents indexed
    double N = m_instanceRefs.size();
    // Iterate through each of the tokens in the index 
    Iterator mapEntries = m_tokenHash.entrySet().iterator();
    while (mapEntries.hasNext()) {
      // Get the token and the tokenInfo for each entry in the HashMap
      Map.Entry entry = (Map.Entry)mapEntries.next();
      String token = (String)entry.getKey();
      TokenInfo tokenInfo = (TokenInfo)entry.getValue();

      // Get the total number of strings in which this token occurs
      double numInstanceRefs = tokenInfo.occList.size(); 

      // Calculate the IDF factor for this token
      double idf = Math.log(N/numInstanceRefs);
      if (idf == 0.0) 
	// If IDF is 0, then just remove this inconsequential token from the index
	mapEntries.remove();
      else {
	tokenInfo.idf = idf;
	// In order to compute document vector lengths,  sum the
	// square of the weights (IDF * occurrence count) across
	// every token occurrence for each document.
	for(int i = 0; i < tokenInfo.occList.size(); i++) {
	  TokenInstanceOccurrence occ = (TokenInstanceOccurrence)tokenInfo.occList.get(i);
	  if (m_useIDF) { 
	    occ.instanceRef.length = occ.instanceRef.length + Math.pow(idf*occ.count, 2);
	  } else {
	    occ.instanceRef.length = occ.instanceRef.length + occ.count * occ.count;
	  }
	}
      }
    }
    // At this point, every document length should be the sum of the squares of
    // its token weights.  In order to calculate final lengths, just need to
    // set the length of every document reference to the square-root of this sum.
    for(int i = 0; i < m_instanceRefs.size(); i++) {
      InstanceReference instanceRef = (InstanceReference)m_instanceRefs.get(i);
      instanceRef.length = Math.sqrt(instanceRef.length);
    }
  }


  /** Populate m_pairSet with all the instancePairs that contain common tokens, so that
   * they can be retrieved in the order of decreasing similarity later
   */
  public void createPairSet() {
    HashSet processedPairSet = new HashSet();
    
    // Iterate through each of the tokens in the index, getting instances containing them
    Iterator mapEntries = m_tokenHash.entrySet().iterator();
    while (mapEntries.hasNext()) {
      // Get the token and the tokenInfo for each entry in the HashMap
      Map.Entry entry = (Map.Entry)mapEntries.next();
      String token = (String)entry.getKey();
      TokenInfo tokenInfo = (TokenInfo)entry.getValue();

      // Get the total number of strings in which this token occurs
      int numInstanceRefs = tokenInfo.occList.size();
      // if more than 1, compare pair and add to the index
      if (numInstanceRefs > 1) {
	for (int i = 0; i < numInstanceRefs; i++) {
	  InstanceReference instRef1 = ((TokenInstanceOccurrence) tokenInfo.occList.get(i)).instanceRef;
	  for (int j = i+1; j < numInstanceRefs; j++) {
	    InstanceReference instRef2 = ((TokenInstanceOccurrence) tokenInfo.occList.get(j)).instanceRef;
	    Integer hashValue1 = new Integer(instRef1.idx * m_instances.numInstances() + instRef2.idx);
	    Integer hashValue2 = new Integer(instRef2.idx * m_instances.numInstances() + instRef1.idx);
	    // if the similarity for this pair of instances has not been calculated before, calculate and store
	    if (!processedPairSet.contains(hashValue1)) {
	      double sim = similarity(instRef1, instRef2);

              if (sim > m_simThreshold) { 

                // make sure that true duplicates are included
                if (m_includeAllDuplicates) { 
                  if (instRef1.instance.classValue() == instRef2.instance.classValue()) 
                    sim = 1e3+i*numInstanceRefs+j;
                }
                
                InstancePair pair = new InstancePair(instRef1.instance, instRef2.instance,
                                                     (instRef1.instance.classValue() == instRef2.instance.classValue()),
                                                     sim); 
                m_pairSet.add(pair); 
                processedPairSet.add(hashValue1);
                processedPairSet.add(hashValue2);
              }
	    }
	  }
	}
      }
    }
  } 


  /** Compute similarity between two strings
   * @param s1 first string
   * @param s2 second string
   * @returns similarity between two strings
   */
  public double similarity(InstanceReference iRef1, InstanceReference iRef2) {
    double length1 = iRef1.length;
    double length2 = iRef2.length;
    HashMapVector v1 = iRef1.vector;
    HashMapVector v2 = iRef2.vector;
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
	TokenInfo tokenInfo = (TokenInfo) m_tokenHash.get(token);

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

  /** Return n most similar pairs
   */
  public InstancePair[] getMostSimilarPairs(int numPairs) {
    Iterator iterator = m_pairSet.iterator();
    int i = 0;
    numPairs = (numPairs < m_pairSet.size()
                ? numPairs : m_pairSet.size()); 
    InstancePair [] pairs = new InstancePair[numPairs]; 
    while (iterator.hasNext() && i < numPairs) {
      InstancePair pair = (InstancePair) iterator.next();
      pairs[i++] = pair;
      //      System.out.println(pair.value + "\t" + pair.positive);
    }
    return pairs; 
  } 
  

  /** Return the number of tokens indexed.
   * @return the number of tokens indexed*/
  public int size() {
    return m_tokenHash.size();
  }


  /** Set the tokenizer to use
   * @param tokenizer the tokenizer that is used
   */
  public void setTokenizer(Tokenizer tokenizer) {
    m_tokenizer = tokenizer;
  }

  /** Get the tokenizer to use
   * @return the tokenizer that is used
   */
  public Tokenizer getTokenizer() {
    return m_tokenizer;
  }

  /** Turn IDF weighting on/off
   * @param useIDF if true, all token weights will be weighted by IDF
   */
  public void setUseIDF(boolean useIDF) { m_useIDF = useIDF;  } 
  public boolean getUseIDF() {  return m_useIDF; } 


  /** Set/get the similarity threshold */
  public void setSimThreshold(double threshold) { m_simThreshold = threshold; }
  public double getSimThreshold() { return m_simThreshold; }

  
  /**
   * Gets the current settings of the Blocker
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [3];
    int current = 0;

    options[current++] = "-minSim" + m_simThreshold;

    if (m_useIDF == false) {
      options[current++] = "-noIDF";
    }

    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Gets a string containing current date and time.
   *
   * @return a string containing the date and time.
   */
  protected static String getTimestamp() {
    return (new SimpleDateFormat("HH:mm:ss:")).format(new Date());
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


















