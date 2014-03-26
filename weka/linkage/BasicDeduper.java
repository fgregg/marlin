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
 *    BasicDeduper.java
 *    Copyright (C) 2003 Mikhail Bilenko
 *
 */

package weka.linkage;

import weka.core.*;
import weka.linkage.metrics.*;
import weka.linkage.blocking.*;
import java.text.SimpleDateFormat;

import java.io.Serializable;
import java.util.*;



/** A basic deduper class that takes a set of objects and
 * links the most similar pairs iteratively. 
 *
 * @author Mikhail Bilenko (mbilenko@cs.utexas.edu)
 * @version $Revision: 1.1.1.1 $
 */
public class BasicDeduper extends Deduper
  implements OptionHandler, Serializable {

  /** A metric measuring similarity between every pair of instances */
  InstanceMetric m_metric = new SumInstanceMetric();

  /** The proportion of the training fold that should be used for training*/
  protected double  m_trainProportion = 1.0;
    
  /** the attribute indeces on which to do deduping */
  protected int[] m_attrIdxs = null;

  /** The total number of true objects */
  protected int m_numObjects = 0;
  
  /** A set of instances to dedupe */
  protected Instances m_testInstances = null;

  /** Use blocking ? */
  protected boolean m_useBlocking = false;

  /** Blocking method to use */
  protected Blocking m_blocking = new NonLearnableBlocking();

  /** This ratio times true number of dupe pairs will be selected
   *  Actual number of pairs may be smaller if blocking returned fewer pairs */
  protected int m_blockingPairRatio = 50;

  /** Artificially add pairs missed by blocking */
  protected boolean m_blockingAddMissedPairs = true; 


  /** verbose? */
  protected boolean m_debug = false;

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

  protected double m_trainTime = 0;
  protected double m_testTimeStart = 0;
  
  /** Given training data, build the metrics required by the deduper
   * @param train a set of training data
   */
  public void buildDeduper(Instances trainData,
                           Instances testData) throws Exception {
    if (m_trainProportion != 1.0) { 
      trainData = getTrainingSet(trainData);
    } 
    
    m_numTotalPairsTrain = trainData.numInstances() * (trainData.numInstances() - 1) / 2;
    m_numPotentialDupePairsTrain = numTruePairs(trainData);
    m_numPotentialNonDupePairsTrain = m_numTotalPairsTrain - m_numPotentialDupePairsTrain;

    // if the indexes have not been set, use all except for class
    if (m_attrIdxs == null) {
      m_attrIdxs = new int[trainData.numAttributes() - 1];
      int classIdx = trainData.classIndex();
      int counter = 0;
      for (int i = 0; i < m_attrIdxs.length + 1; i++) {
	if (i != classIdx) {
	  m_attrIdxs[counter++] = i;
	}
      } 
    }

    // START TRAINING
    long trainTimeStart = System.currentTimeMillis();
    
    // train the blocking
    if (m_useBlocking) {
      m_blocking.learnBlocking(trainData);
    }

    // train the instance metric
    m_metric.buildInstanceMetric(m_attrIdxs);
    m_metric.trainInstanceMetric(trainData, testData);

    // get training statistics
    m_numActualDupePairsTrain = m_metric.getNumActualPosPairs();   
    m_numActualNonDupePairsTrain = m_metric.getNumActualNegPairs();  
      
    m_trainTime = (System.currentTimeMillis() - trainTimeStart)/1000.0;
  }


  /** Create pairs and get the statistics */
  public void findDuplicates(Instances testData,
                             int numObjects) throws Exception  {
    m_numObjects = testData.numClasses();
    m_numTruePairs = numTruePairs(testData);

    int numInstances = testData.numInstances();
    m_numTotalPairsTest = numInstances * (numInstances -1) / 2;
    resetStatistics();
    
    // Set the weight of each instance to its index
    for (int i = 0; i < numInstances; i++) {
      testData.instance(i).setWeight(i); 
    } 
    
    InstancePair[] pairs;

    if (m_useBlocking) {
      System.out.println("\nUsing blocking...");

      if (m_blockingAddMissedPairs) { 
        m_blocking.setIncludeAllDuplicates(true);
      } 
      m_blocking.buildIndex(testData);

      // requested number of pairs is m_numTruePairs*m_blockingPairRatio or totalPairs
      int numRequestedPairs = (m_numTotalPairsTest >= m_numTruePairs * m_blockingPairRatio 
                               ? m_numTruePairs * m_blockingPairRatio
                               : m_numTotalPairsTest);
      System.out.println("Requesting " + numRequestedPairs + " out of " + m_numTotalPairsTest); 
      pairs = m_blocking.getMostSimilarPairs(numRequestedPairs);

      // Count good pairs to make sure all is well
      int goodPairs = 0;
      System.out.println(pairs.length); 
      for (InstancePair pair : pairs) {
        if (pair.positive)
          ++goodPairs;
      }
      System.out.println("Got " + pairs.length + " pairs from blocking. " +
                         "True pairs:" + m_numTruePairs + " good blocked pairs:" +
                         goodPairs);
      if (goodPairs != m_numTruePairs) {
        System.err.println("\n\n*******  BLOCKING MISSED " +
                           (m_numTruePairs - goodPairs) + " PAIRS:*****\n");

        // investigate: find pairs missed by blocking
        if (m_debug) { 
          // populate blocked good pairs
          HashMap<Instance, Set<Instance>>
            blockedMap = new HashMap<Instance, Set<Instance>>();
          for (InstancePair pair : pairs) {
            if (pair.instance1.classValue() == pair.instance2.classValue()) {
              Set<Instance> set1 = blockedMap.get(pair.instance1);
              if (set1 == null) {
                set1 = new HashSet<Instance>();
              }
              set1.add(pair.instance2);
              blockedMap.put(pair.instance1, set1);

              Set<Instance> set2 = blockedMap.get(pair.instance2);
              if (set2 == null) {
                set2 = new HashSet<Instance>();
              }
              set2.add(pair.instance1);
              blockedMap.put(pair.instance2, set2);
            }
          }

          // create the classmap
          HashMap<Double, List<Instance>> classMap =
            new HashMap<Double, List<Instance>>();
          for (int i = 0; i < numInstances; i++) {
            Instance instance = testData.instance(i);
            Double classValue = new Double(instance.classValue());
            List<Instance> iList= classMap.get(classValue);
            if (iList == null) {
              iList = new ArrayList<Instance>();
            }
            iList.add(instance);
            classMap.put(classValue, iList);
          }

          // go through class map, making sure pairs are present
          for (List<Instance> iList : classMap.values()) {
            for (int i1 = 0; i1 < iList.size()-1; i1++) {
              Instance instance1 = iList.get(i1);
              Set<Instance> blockedSet1 = blockedMap.get(instance1);
              for (int i2 = i1+1; i2 < iList.size(); i2++) {
                Instance instance2 = iList.get(i2);
                if (blockedSet1 == null || !blockedSet1.contains(instance2)) {
                  System.out.println("Found missing pair!!!!: ");
                  System.out.println("\t" + instance1);
                  System.out.println("\t" + instance2); 
                } 
              
              }
            } 
          }
        }
        // END OF HANDLING MISSING PAIRS DUE TO BLOCKING
      } 
    } else {
      System.out.println("*NOT* using blocking..."); 
      
      // create an array of all pairs
      pairs = new InstancePair[numInstances*(numInstances - 1)/2];
      int pairIdx = 0; 
      for (int i = 0; i < numInstances-1; i++) {
        Instance instance1 = testData.instance(i);
        for (int j = i+1; j < numInstances; j++) {
          Instance instance2 = testData.instance(j);
          boolean sameClass = (instance1.classValue() == instance2.classValue());
          pairs[pairIdx++] = new InstancePair(instance1, instance2, sameClass, 0);
        }
      }
    }

    // compute similarity between all pairs of interest
    int numPairs = pairs.length;

    for (int i = 0; i < numPairs; i++) {
      InstancePair pair = pairs[i]; 
      pair.value = m_metric.distance(pair.instance1, pair.instance2);
      if (i % 100 == 99) System.out.print("#");
      if (i % 10000 == 9999) System.out.println(" " + (1+i));
    }
    System.out.println(getTimestamp() + ": done calculating distances\n");

    // sort the pairs array by distance
    Arrays.sort(pairs);

    accumulateStatistics(pairs); 

    // If in verbose mode, print out the errors
    if (m_debug) { 
      for (int i = 0; i < numPairs; i++) {
        InstancePair pair = pairs[i];
      
        if (pair.positive) {
          ++m_numGoodPairs;
        }
        m_numTotalPairs = i+1;
                  
        if (!pair.positive && m_numTotalPairs < m_numTruePairs) {
          System.out.println("FALSE POSITIVE:  " +
                             pair.instance1.stringValue(pair.instance1.classAttribute()) + " = " +
                             pair.instance2.stringValue(pair.instance2.classAttribute())); 
          System.out.println("\t" + pair.instance1);
          System.out.println("\t" + pair.instance2);
        }
        if (pair.positive && m_numTotalPairs >  m_numTruePairs) {
          System.out.println("HARD POSITIVE: " +
                             pair.instance1.stringValue(pair.instance1.classAttribute()) + " = " +
                             pair.instance2.stringValue(pair.instance2.classAttribute())); 
          System.out.println("\t" + pair.instance1);
          System.out.println("\t" + pair.instance2);
        }
      }
    }

    pairs = null; 
    System.out.println(getTimestamp() + ": done collecting statistics\n");
  }


  /** A helper function that stratifies a training set and selects a proportion of
   * true objects for training
   * @param instances a set of instances from which to select the training data
   * @return a subset of those instances
   */
  Instances getTrainingSet(Instances instances) {
    HashMap classHash = new HashMap();
    int numTotalInstances = instances.numInstances();
    Random rand = new Random(numTotalInstances);
    Instances trainInstances = new Instances(instances, (int) (m_trainProportion * numTotalInstances));

    // hash each class 
    for (int i=0; i < instances.numInstances(); i++) {
      Instance instance = instances.instance(i);
      Double classValue = new Double(instance.classValue());
      if (classHash.containsKey(classValue)) {
	ArrayList list = (ArrayList) classHash.get(classValue);
	list.add(instance);
      } else {
	// this class has not been seen before, create an entry for it
	ArrayList list = new ArrayList();
	list.add(instance);
	classHash.put(classValue, list);
      }
    }

    // select a desired proportion of classes
    ArrayList[] classes = new ArrayList[classHash.size()];
    classes = (ArrayList[]) classHash.values().toArray(classes);
    int numClasses = classes.length;
    int[] indeces = PairwiseSelector.randomSubset((int) (m_trainProportion * numClasses), numClasses);

    for (int i = 0; i < indeces.length; i++) {
      for (int j = 0; j < classes[i].size(); j++) {
	Instance instance = (Instance) classes[i].get(j);
	trainInstances.add(instance);
      }
    } 

    return trainInstances;
  }


  
  /** Add the current state of things to statistics */
  protected void accumulateStatistics(InstancePair[] pairs) {
    int numRecLevels = 101;
    double[] recLevels = new double[numRecLevels];
    double[] iPrec = new double[numRecLevels]; 

    double recallInc = 1.0 / (numRecLevels-1); 
    for (int i = 0; i < numRecLevels; i++) {
      recLevels[i] = recallInc * i;
    }

    int numPairs = pairs.length;

    // get the interpolated precisions and MAP

    int currRecallIdx = 0;
    double maxF1 = 0;
    double avgPrec = 0; 
    int numGoodPairs = 0;
    
    for (int i = 0; i < numPairs; i++) {

      // if good pair (move along recall axis)
      if (pairs[i].positive) {
        ++numGoodPairs; 
        double prec = (numGoodPairs + 0.0) / (i+1); 
        double recall = (numGoodPairs + 0.0) / m_numTruePairs;
        
        double f1 = ((recall + prec) > 0)
          ? 2.0 * recall * prec / (recall + prec)
          : 0.0;

        if (f1 > maxF1) maxF1 = f1; 
        avgPrec += prec;
        

        // need to update the interpolated values? 
        while (currRecallIdx < numRecLevels-1 &&
               recall >= recLevels[currRecallIdx+1]) {
          ++currRecallIdx;
        } 
        
        // update the interpolated precisions if necessary
        for (int j = currRecallIdx; j >= 0; j--) {
          if (iPrec[j] < prec) {
            iPrec[j] = prec;
          } else {
            break;
          } 
        }

        if (recall == 1.0) break;
      } 
    }

    double map = avgPrec / m_numTruePairs; 


    for (int i = 0; i < numRecLevels; i++) { 
      Object[] currentStats = new Object[17];
      int statIdx = 0;
      currentStats[statIdx++] = new Double(0);

      // Accuracy statistics
      currentStats[statIdx++] = new Double(recLevels[i]);
      currentStats[statIdx++] = new Double(iPrec[i]);
      currentStats[statIdx++] = new Double(maxF1);
      currentStats[statIdx++] = new Double(map);

      // Dupe density statistics
      currentStats[statIdx++] = new Double(m_numTotalPairsTrain);
      currentStats[statIdx++] = new Double(m_numPotentialDupePairsTrain);
      currentStats[statIdx++] = new Double(m_numActualDupePairsTrain);
      currentStats[statIdx++] = new Double(m_numPotentialNonDupePairsTrain);
      currentStats[statIdx++] = new Double(m_numActualNonDupePairsTrain);
      currentStats[statIdx++] = new Double((m_numActualNonDupePairsTrain > 0)
                                           ? ((m_numActualDupePairsTrain+0.0)/
                                              m_numActualNonDupePairsTrain)
                                           : 0);
      currentStats[statIdx++] = new Double((m_numPotentialDupePairsTrain+0.0)/
                                           m_numTotalPairsTrain);
      currentStats[statIdx++] = new Double(m_numTotalPairsTest);    
      currentStats[statIdx++] = new Double(m_numTruePairs);     
      currentStats[statIdx++] = new Double((m_numTruePairs + 0.0)/
                                           m_numTotalPairsTest);

      // Timing statistics
      currentStats[statIdx++] = new Double(m_trainTime);
      currentStats[statIdx++] = new Double((System.currentTimeMillis() -
                                            m_testTimeStart)/1000.0);

      m_statistics.add(currentStats);
    }
  }


  /** Reset the current statistics */
  protected void resetStatistics() {
    m_statistics = new ArrayList();
    m_numGoodPairs = 0;
    m_numTotalPairs = 0;
    m_testTimeStart = System.currentTimeMillis();
  } 

  
  /** Given a test set, calculate the number of true pairs
   * @param instances a set of objects, class has the true object ID
   * @returns the number of true same-class pairs
   */
  protected int numTruePairs(Instances instances) {
    int numTruePairs = 0;
    // get the class counts
    HashMap<Double, Integer> classCountMap = new HashMap<Double, Integer>();

    for (int i = 0; i < instances.numInstances(); i++) {
      Instance instance = instances.instance(i);
      Double classValue = new Double(instance.classValue());
      Integer counts = classCountMap.get(classValue);
      if (counts == null) {
        counts = new Integer(0);
      }
      classCountMap.put(classValue, counts + 1);
    }
    
    // calculate the number of pairs
    for (Integer counts : classCountMap.values()) { 
      numTruePairs += counts * (counts - 1) / 2;
    }
    return numTruePairs;
  }
  


  /** Set/get the InstanceMetric */
  public void setMetric(InstanceMetric metric) { m_metric = metric;  }
  public InstanceMetric getMetric() { return m_metric; }

  
  /** Turn debugging output on/off */
  public void setDebug(boolean debug) { m_debug = debug; }
  public boolean getDebug() {  return m_debug; }


  /** Turn blocking on/off  */
  public void setBlocking(boolean useBlocking) { m_useBlocking = useBlocking; }
  public boolean getBlocking() { return m_useBlocking; } 

  
  /** Set/get the blocking pair ratio */
  public void setBlockingPairRatio(int ratio) { m_blockingPairRatio = ratio; }
  public int getBlockingPairRatio() { return m_blockingPairRatio; } 

  
  /** Set/get the blocking method */
  public void setBlockingMethod(Blocking blocking) {  m_blocking = blocking; }
  public Blocking getBlockingMethod() { return m_blocking; } 

  
  /** Turn adding of pairs missed by blocking on/off  */
  public void setBlockingAddMissedPairs(boolean b) { m_blockingAddMissedPairs = b; }
  public boolean getBlockingAddMissedPairs() { return m_blockingAddMissedPairs; } 
  
  

  /** Set/get the proportion of training fold used for actual training */
  public void setTrainProportion(double trainProportion) {  m_trainProportion = trainProportion; }
  public double getTrainProportion() {  return m_trainProportion;  }

  
  /**
   * Returns an enumeration describing the available options
   *
   * @return an enumeration of all the available options
   **/
  public Enumeration listOptions() {
    Vector newVector = new Vector(2);
    newVector.addElement(new Option("\tMetric.\n"
				    +"\t(default=ClassifierInstanceMetric)", "M", 1,"-M metric_name metric_options"));
    return newVector.elements();
  }

  /**
   * Parses a given list of options.
   *
   * Valid options are:<p>
   *
   * -M metric options <p>
   * InstanceMetric used <p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   *
   **/
  public void setOptions(String[] options) throws Exception {
    String optionString;

    String metricString = Utils.getOption('M', options);
    if (metricString.length() != 0) {
      String[] metricSpec = Utils.splitOptions(metricString);
      String metricName = metricSpec[0]; 
      metricSpec[0] = "";
      System.out.println("Metric name: " + metricName + "\nMetric parameters: "
                         + concatStringArray(metricSpec));
      setMetric(InstanceMetric.forName(metricName, metricSpec));
    }
  }


  /**
   * Gets the current settings of Greedy Agglomerative Clustering
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [250];
    int current = 0;

    if (m_useBlocking == false) { 
      options[current++] = "-NB"; 
    } else {
      options[current++] = "-B"+ m_blockingPairRatio;
      options[current++] = Utils.removeSubstring(m_blocking.getClass().getName(), "weka.linkage.blocking.");
      if (m_blocking instanceof OptionHandler) {
        String[] blockingOptions = ((OptionHandler)m_blocking).getOptions();
        for (int i = 0; i < blockingOptions.length; i++) {
          options[current++] = blockingOptions[i];
        }
      } 
    }

    if (m_trainProportion < 1.0) { 
      options[current++] = "-T";
      options[current++] = "" + m_trainProportion;
    }

    if (m_debug) {
      options[current++] = "-D";
    }

    options[current++] = "-M";
    String metricName = Utils.removeSubstring(m_metric.getClass().getName(),
                                              "weka.linkage.metrics.");
    options[current++] = Utils.removeSubstring(metricName,
                                               "InstanceMetric");
    if (m_metric instanceof OptionHandler) {
      String[] metricOptions = ((OptionHandler)m_metric).getOptions();
      for (int i = 0; i < metricOptions.length; i++) {
	options[current++] = metricOptions[i];
      }
    } 

    while (current < options.length) {
      options[current++] = "";
    }

    return options;
  }

  /** Gets a string containing current date and time.   */
  public static String getTimestamp() {
    return (new SimpleDateFormat("HH:mm:ss:")).format(new Date());
  }


  /** A little helper to create a single String from an array of Strings
   * @param strings an array of strings
   * @returns a single concatenated string
   */
  public static String concatStringArray(String[] strings) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < strings.length; i++) {
      buffer.append(strings[i]);
      buffer.append(" ");
    }
    return buffer.toString();
  } 
}







