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
 *    LinkagePRCurveCVResultProducer.java
 *    Copyright (c) 2003 Mikhail Bilenko
 *
 */


package weka.linkage;

import java.util.*;
import java.io.*;
import weka.core.*;
import weka.experiment.*;
import weka.core.OptionHandler;
import weka.core.Option;
import weka.core.Utils;
import weka.core.AdditionalMeasureProducer;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;


/**
 * N-fold cross-validation learning curve for
 * deduping applications
 *
 * @author Mikhail Bilenko */

public class  LinkagePRCurveCVResultProducer 
  implements ResultProducer, OptionHandler, AdditionalMeasureProducer {
  
  /** The dataset of interest */
  protected Instances m_instances;

  /** SVM-light can work in classification, regression and preference ranking modes */
  public static final int FOLD_CREATION_MODE_STRATIFIED = 1;
  public static final int FOLD_CREATION_MODE_RANDOM = 2;
  public static final Tag[] TAGS_FOLD_CREATION_MODE = {
    new Tag(FOLD_CREATION_MODE_STRATIFIED, "Stratified"),
    new Tag(FOLD_CREATION_MODE_RANDOM, "Random")
  };
  protected int m_foldCreationMode = FOLD_CREATION_MODE_STRATIFIED;

  /** The ResultListener to send results to */
  protected ResultListener m_resultListener = new CSVResultListener();

  /** The number of folds in the cross-validation */
  protected int m_numFolds = 2;

  /** Can run for a specific fold */
  protected int m_startingFold = 0; 
    
  /** Save raw output of split evaluators --- for debugging purposes */
  protected boolean m_debugOutput = false;

  /** The output zipper to use for saving raw splitEvaluator output */
  protected OutputZipper m_zipDest = null;

  /** The destination output file/directory for raw output */
  protected File m_outputFile = new File(
				new File(System.getProperty("user.dir")), 
				"splitEvalutorOut.zip");

  /** The SplitEvaluator used to generate results */
  protected SplitEvaluator m_splitEvaluator = new LinkageSplitEvaluator();

  /** The names of any additional measures to look for in SplitEvaluators */
  protected String [] m_additionalMeasures = null;

  /** The specific points to plot, either integers representing specific numbers of training examples,
   * or decimal fractions representing percentages of the full training set*/
  protected double[] m_plotPoints = {0.00, 0.02, 0.04, 0.06, 0.08,
                                     0.10, 0.12, 0.14, 0.16, 0.18,
                                     0.20, 0.22, 0.24, 0.26, 0.28,
                                     0.30, 0.32, 0.34, 0.36, 0.38,
                                     0.40, 0.42, 0.44, 0.46, 0.48,
                                     0.50, 0.52, 0.54, 0.56, 0.58,
                                     0.60, 0.62, 0.64, 0.66, 0.68,
                                     0.70, 0.72, 0.74, 0.76, 0.78,
                                     0.80, 0.82, 0.84, 0.86, 0.88,
                                     0.90, 0.92, 0.94, 0.96, 0.98, 1.00};


  /* The name of the key field containing the dataset name */
  public static String DATASET_FIELD_NAME = "Dataset";

  /* The name of the key field containing the run number */
  public static String RUN_FIELD_NAME = "Run";

  /* The name of the key field containing the fold number */
  public static String FOLD_FIELD_NAME = "Fold";

  /* The name of the result field containing the timestamp */
  public static String TIMESTAMP_FIELD_NAME = "Date_time";

  /* The name of the key field containing the learning rate step number */
  public static String RECALL_FIELD_NAME = "Fraction_instances";

  /**
   * Returns a string describing this result producer
   * @return a description of the result producer suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return "Performs a learning-curve cross validation run using a supplied " +
      "deduping  split evaluator. Trains on increasing subsets of the training data for each split";
  }

  /**
   * Sets the dataset that results will be obtained for.
   *
   * @param instances a value of type 'Instances'.
   */
  public void setInstances(Instances instances) {
    m_instances = instances;
  }

  /**
   * Sets the object to send results of each run to.
   *
   * @param listener a value of type 'ResultListener'
   */
  public void setResultListener(ResultListener listener) {
    m_resultListener = listener;
  }

  /**
   * Set a list of method names for additional measures to look for
   * in SplitEvaluators. This could contain many measures (of which only a
   * subset may be produceable by the current SplitEvaluator) if an experiment
   * is the type that iterates over a set of properties.
   * @param additionalMeasures an array of measure names, null if none
   */
  public void setAdditionalMeasures(String [] additionalMeasures) {
    m_additionalMeasures = additionalMeasures;
    if (m_splitEvaluator != null) {
      System.err.println(" LinkagePRCurveCVResultProducer: setting additional "
			 +"measures for "
			 +"split evaluator");
      m_splitEvaluator.setAdditionalMeasures(m_additionalMeasures);
    }
  }

  /**
   * Returns an enumeration of any additional measure names that might be
   * in the SplitEvaluator
   * @return an enumeration of the measure names
   */
  public Enumeration enumerateMeasures() {
    Vector newVector = new Vector();
    if (m_splitEvaluator instanceof AdditionalMeasureProducer) {
      Enumeration en = ((AdditionalMeasureProducer)m_splitEvaluator).
	enumerateMeasures();
      while (en.hasMoreElements()) {
	String mname = (String)en.nextElement();
	newVector.addElement(mname);
      }
    }
    return newVector.elements();
  }
  
  /**
   * Returns the value of the named measure
   * @param measureName the name of the measure to query for its value
   * @return the value of the named measure
   * @exception IllegalArgumentException if the named measure is not supported
   */
  public double getMeasure(String additionalMeasureName) {
    if (m_splitEvaluator instanceof AdditionalMeasureProducer) {
      return ((AdditionalMeasureProducer)m_splitEvaluator).
	getMeasure(additionalMeasureName);
    } else {
      throw new IllegalArgumentException("LinkagePRCurveCVResultProducer: "
					 +"Can't return value for : "+additionalMeasureName
					 +". "+m_splitEvaluator.getClass().getName()+" "
					 +"is not an AdditionalMeasureProducer");
    }
  }
  
  /**
   * Gets a Double representing the current date and time.
   * eg: 1:46pm on 20/5/1999 -> 19990520.1346
   *
   * @return a value of type Double
   */
  public static Double getTimestamp() {

    Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    double timestamp = now.get(Calendar.YEAR) * 10000
      + (now.get(Calendar.MONTH) + 1) * 100
      + now.get(Calendar.DAY_OF_MONTH)
      + now.get(Calendar.HOUR_OF_DAY) / 100.0
      + now.get(Calendar.MINUTE) / 10000.0;
    return new Double(timestamp);
  }
  
  /**
   * Prepare to generate results.
   *
   * @exception Exception if an error occurs during preprocessing.
   */
  public void preProcess() throws Exception {

    if (m_splitEvaluator == null) {
      throw new Exception("No SplitEvalutor set");
    }
    if (m_resultListener == null) {
      throw new Exception("No ResultListener set");
    }
    m_resultListener.preProcess(this);
  }
  
  /**
   * Perform any postprocessing. When this method is called, it indicates
   * that no more requests to generate results for the current experiment
   * will be sent.
   *
   * @exception Exception if an error occurs
   */
  public void postProcess() throws Exception {
    m_resultListener.postProcess(this);
    if (m_debugOutput) {
      if (m_zipDest != null) {
	m_zipDest.finished();
	m_zipDest = null;
      }
    }
  }
  
  /**
   * Gets the keys for a specified run number. Different run
   * numbers correspond to different randomizations of the data. Keys
   * produced should be sent to the current ResultListener
   *
   * @param run the run number to get keys for.
   * @exception Exception if a problem occurs while getting the keys
   */
  public void doRunKeys(int run) throws Exception {
    int numExtraKeys = 4;

    if (m_instances == null) {
      throw new Exception("No Instances set");
    }
    if (m_resultListener == null) {
      throw new Exception("No ResultListener set");
    }
    for (int fold = m_startingFold; fold < m_numFolds; fold++) {  
      int pointNum = 0;
      // For each subsample size
      for (int i = 0; i < m_plotPoints.length; i++) {
	// Add in some fields to the key like run and fold number, dataset name
	Object [] seKey = m_splitEvaluator.getKey();
		Object [] key = new Object [seKey.length + numExtraKeys];
	key[0] = Utils.backQuoteChars(m_instances.relationName());
	key[1] = "" + run;
	key[2] = "" + (fold + 1);
	key[3] = "" + m_plotPoints[i];
	System.arraycopy(seKey, 0, key, numExtraKeys, seKey.length);
	if (m_resultListener.isResultRequired(this, key)) {
	  try {
	    m_resultListener.acceptResult(this, key, null);
	  } catch (Exception ex) {
	    // Save the train and test datasets for debugging purposes?
	    throw ex;
	  }
	}
      }
    }
  }

  /**
   * Gets the results for a specified run number. Different run
   * numbers correspond to different randomizations of the data. Results
   * produced should be sent to the current ResultListener
   *
   * @param run the run number to get results for.
   * @exception Exception if a problem occurs while getting the results
   */
  public void doRun(int run) throws Exception {
    int numExtraKeys = 4;

    if (getRawOutput()) {
      if (m_zipDest == null) {
	m_zipDest = new OutputZipper(m_outputFile);
      }
    }
    if (m_instances == null) {
      throw new Exception("No Instances set");
    }
    if (m_resultListener == null) {
      throw new Exception("No ResultListener set");
    }
    if (!m_instances.classAttribute().isNominal()) {
      throw new Exception("Class attribute must be nominal - it is the true Object ID");
    }
    // Randomize on a copy of the original dataset
    Instances runInstances = new Instances(m_instances);
    runInstances.randomize(new Random(run));
    ArrayList foldList = createFoldList(runInstances, m_numFolds);

    for (int fold = m_startingFold; fold < m_numFolds; fold++) { 
      Instances train = getTrainingFold(foldList, fold);
      // Randomly shuffle the  training set for fold creation
      train.randomize(new Random(fold));	    

      Instances test = (Instances) foldList.get(fold);
      System.out.println("Run:" + run + " Fold:" + fold + " TestSize=" + test.numInstances());

      // check if we need to run this fold
      Object [] seKey = m_splitEvaluator.getKey();      
      Object [] testKey = new Object [seKey.length + numExtraKeys];
      testKey[0] = Utils.backQuoteChars(m_instances.relationName());
      testKey[1] = "" + run;
      testKey[2] = "" + (fold + 1);
      testKey[3] = "" + m_plotPoints[0];
      System.arraycopy(seKey, 0, testKey, numExtraKeys, seKey.length);
      if (m_resultListener.isResultRequired(this, testKey)) {
        Object[] prResults = m_splitEvaluator.getResult(train, test);

      
        for (int i = 0; i < m_plotPoints.length; i++) {
          // Add in some fields to the key like run and fold number, dataset name
          Object [] key = new Object [seKey.length + numExtraKeys];
          key[0] = Utils.backQuoteChars(m_instances.relationName());
          key[1] = "" + run;
          key[2] = "" + (fold + 1);
          key[3] = "" + m_plotPoints[i];
          System.arraycopy(seKey, 0, key, numExtraKeys, seKey.length);
          if (m_resultListener.isResultRequired(this, key)) {
            try {
              Object [] seResults = processResults(prResults, m_plotPoints[i]);
              System.out.println("Adding result:  RLevel=" + m_plotPoints[i] +
                                 "\tR=" + seResults[1] + "\tP=" + seResults[2] +
                                 "\tFM=" + seResults[3]); 
              Object [] results = new Object [seResults.length + 1];
              results[0] = getTimestamp();
              System.arraycopy(seResults, 0, results, 1,
                               seResults.length);
              if (m_debugOutput) {
                String resultName = (""+run+"."+(fold+1)+"."+ "." 
                                     + Utils.backQuoteChars(runInstances.relationName())
                                     +"."
                                     +m_splitEvaluator.toString()).replace(' ','_');
                resultName = Utils.removeSubstring(resultName, 
                                                   "weka.clusterers.");
                resultName = Utils.removeSubstring(resultName, 
                                                   "weka.filters.");
                resultName = Utils.removeSubstring(resultName, 
                                                   "weka.attributeSelection.");
                resultName = Utils.removeSubstring(resultName, 
                                                   "weka.linkage.");
                m_zipDest.zipit(m_splitEvaluator.getRawResultOutput(), resultName);
              }
              m_resultListener.acceptResult(this, key, results);
            } catch (Exception ex) {
              // Save the train and test datasets for debugging purposes?
              throw ex;
            }
          }
        }
      }
    }
  }


  /** Given a set of instances with the class attribute containing true
   * objectID, create a list of folds using the preferred method
   *
   * @param runInstances a set of instances with class labels
   * @param numFolds the number of folds to create
   * @return a list of Instances objects, every Instances contains a fold
   */
  ArrayList createFoldList(Instances runInstances, int numFolds) {
    ArrayList foldList = null;
    switch (m_foldCreationMode) {
    case FOLD_CREATION_MODE_STRATIFIED:
      foldList =  createFoldListStratified(runInstances, numFolds);
      break;
    case FOLD_CREATION_MODE_RANDOM:
      foldList =  createFoldListRandom(runInstances, numFolds);
      break;
    default:
      System.err.println("Panic!  Unknown fold creation mode: " + m_foldCreationMode);
      System.exit(1);
    }
    return foldList;
  }
  

  /** Given a set of instances with the class attribute containing true
   * objectID, create a list of folds containing an approximately equal number of class values (equivalence classes)
   * Objects with the same class ID *always* end in the same fold
   *
   * @param runInstances a set of instances with class labels
   * @param numFolds the number of folds to create
   * @return a list of Instances objects, every Instances contains a fold
   */
  ArrayList createFoldListStratified(Instances runInstances, int numFolds) {
    HashMap classFoldHash = new HashMap();
    ArrayList foldList = new ArrayList(numFolds);
    int numInstances = runInstances.numInstances();
    Random rand = new Random(numFolds + numInstances);
	
    // initialize the folds
    for (int i=0; i < numFolds; i++) {
      Instances fold = new Instances(runInstances, numInstances/numFolds);
      foldList.add(fold);
    }

    // assign each class to a random fold
    for (int i=0; i < runInstances.numInstances(); i++) {
      Instance instance = runInstances.instance(i);
      Double classValue = new Double(instance.classValue());
      if (classFoldHash.containsKey(classValue)) {
	Instances fold = (Instances) classFoldHash.get(classValue);
	fold.add(instance);
      } else {
	// this class has not been seen before, assign to a random fold
	int foldIdx = rand.nextInt(numFolds);
	Instances fold = (Instances) foldList.get(foldIdx);
	fold.add(instance);
	classFoldHash.put(classValue, fold);
      }
    }
    return foldList;
  }

  /** Given a set of instances with the class attribute containing true
   * objectID, create a list of *randomly assigned* folds disregarding stratification
   *
   * @param runInstances a set of instances with class labels
   * @param numFolds the number of folds to create
   * @return a list of Instances objects, every Instances contains a fold
   */
  ArrayList createFoldListRandom(Instances runInstances, int numFolds) {
    ArrayList foldList = new ArrayList(numFolds);
    int numInstances = runInstances.numInstances();
    Random rand = new Random(numFolds + numInstances);
	
    // initialize the folds
    for (int i=0; i < numFolds; i++) {
      Instances fold = new Instances(runInstances, numInstances/numFolds);
      foldList.add(fold);
    }

    // Create all positive pairs and assign them to random folds
    // first, hash all classes
    HashMap classInstanceListHash = new HashMap();
    for (int i=0; i < runInstances.numInstances(); i++) {
      Instance instance = runInstances.instance(i);
      Double classValue = new Double(instance.classValue());
      if (classInstanceListHash.containsKey(classValue)) {
	ArrayList instanceList = (ArrayList) classInstanceListHash.get(classValue);
	instanceList.add(instance);
      } else {
	// this class has not been seen before, create a new list for it
	ArrayList instanceList = new ArrayList();
	instanceList.add(instance);
	classInstanceListHash.put(classValue, instanceList);
      }
    }

    int [] foldAssignments = new int[runInstances.numInstances()];
    Arrays.fill(foldAssignments, -1);
    // go through the classes; each pair gets assigned to a random fold; singletons are also thrown
    // into a random fold
    Iterator iterator = classInstanceListHash.values().iterator();
    while (iterator.hasNext()) {
      ArrayList instanceList = (ArrayList) iterator.next();
      int classSize = instanceList.size();
      if (classSize > 1) {
	// go through all pairs and assign both instances of each pair to a random fold
	boolean[][] foldInstancesAdded = new boolean[numFolds][classSize];
	for (int i=0; i < classSize-1; i++) {
	  Instance instance1 = (Instance) instanceList.get(i);
	  for (int j=i+1; j < classSize; j++) {
	    Instance instance2 = (Instance) instanceList.get(j);
	    int foldIdx = rand.nextInt(numFolds);
	    Instances fold = (Instances) foldList.get(foldIdx);

	    // add the two instances to the random fold unless they have previously been added
	    if (foldInstancesAdded[foldIdx][i] == false) { 
	      fold.add(instance1);
	      foldInstancesAdded[foldIdx][i] = true;
	    }
	    if (foldInstancesAdded[foldIdx][j] == false) {
	      fold.add(instance2);
	      foldInstancesAdded[foldIdx][j] = true;
	    }
	  }
	}
      } else {
	// singleton class, assign to a random fold
	Instance instance = (Instance) instanceList.get(0);
	int foldIdx = rand.nextInt(numFolds);
	Instances fold = (Instances) foldList.get(foldIdx);
	fold.add(instance);
      } 
    } 
    return foldList;
  }

  /** Given a list of folds, merge together all but the test fold with the specified index
   * and return the resulting training fold
   * @param foldList a list containg folds
   * @param testFoldIdx the index of the fold that will be used for testing
   * @return an agglomeration of all folds except for the test one.
   */
  protected Instances getTrainingFold(ArrayList foldList, int testFoldIdx) {
    Instances trainFold = new Instances(m_instances, m_instances.numInstances());
    System.out.println("Getting training fold " + testFoldIdx + " out of " + foldList.size()); 
    for (int i = 0; i < foldList.size(); i++) {
      if (i != testFoldIdx) {
	Instances nextFold = (Instances) foldList.get(i);
	for (int j = 0; j < nextFold.numInstances(); j++) {
	  Instance nextInstance = (Instance) nextFold.instance(j);
	  trainFold.add(nextInstance);
	}
      }
    }
    return trainFold;
  }

  /** Given an array containing the overall results of a deduping
   * experiment, produce an array containing results for a specific
   * recall level
   */
  protected Object [] processResults(Object[] prResults, double recallLevel) {
    double maxPrecision = 0;
    double maxFM = 0; 
    Object[] results = (Object[]) prResults[prResults.length-1];
    
    //    System.out.println(results[1] + "\t" + results[2] + "\t" + results[3]);
    for (int i = prResults.length-1; i >= 0; i--) {
      Object[] nextResults = (Object[]) prResults[i];
      //System.out.println(nextResults[1] + "\t" + nextResults[2] + "\t" + nextResults[3]);
      double recall = ((Double) nextResults[1]).doubleValue();
      if (recall < recallLevel) {
	break;
      }
      double precision = ((Double) nextResults[2]).doubleValue();
      if (precision > maxPrecision) {
	maxPrecision = precision;
	results = nextResults;
      }
      double fmeasure = ((Double) nextResults[3]).doubleValue();
      if (fmeasure > maxFM) {
	maxFM = fmeasure;
      }
    }
    Object [] returnResults = new Object [results.length];
    System.arraycopy(results, 0, returnResults, 0, results.length);
    returnResults[1] = new Double(recallLevel);
    returnResults[3] = new Double(maxFM);
    return returnResults;
  }

 
  /**
   * Gets the names of each of the columns produced for a single run.
   * This method should really be static.
   *
   * @return an array containing the name of each column
   */
  public String [] getKeyNames() {
    String [] keyNames = m_splitEvaluator.getKeyNames();
    // Add in the names of our extra key fields
    int numExtraKeys = 4;

    String [] newKeyNames = new String [keyNames.length + numExtraKeys];
    newKeyNames[0] = DATASET_FIELD_NAME;
    newKeyNames[1] = RUN_FIELD_NAME;
    newKeyNames[2] = FOLD_FIELD_NAME;
    newKeyNames[3] = RECALL_FIELD_NAME;
    System.arraycopy(keyNames, 0, newKeyNames, numExtraKeys, keyNames.length);
    return newKeyNames;
  }

  /**
   * Gets the data types of each of the columns produced for a single run.
   * This method should really be static.
   *
   * @return an array containing objects of the type of each column. The 
   * objects should be Strings, or Doubles.
   */
  public Object [] getKeyTypes() {
    Object [] keyTypes = m_splitEvaluator.getKeyTypes();
    int numExtraKeys = 4;

    // Add in the types of our extra fields
    Object [] newKeyTypes = new String [keyTypes.length + numExtraKeys];
    newKeyTypes[0] = new String();
    newKeyTypes[1] = new String();
    newKeyTypes[2] = new String();
    newKeyTypes[3] = new String();
    System.arraycopy(keyTypes, 0, newKeyTypes, numExtraKeys, keyTypes.length);
    return newKeyTypes;
  }

  /**
   * Gets the names of each of the columns produced for a single run.
   * This method should really be static.
   *
   * @return an array containing the name of each column
   */
  public String [] getResultNames() {

    String [] resultNames = m_splitEvaluator.getResultNames();
    // Add in the names of our extra Result fields
    String [] newResultNames = new String [resultNames.length + 1];
    newResultNames[0] = TIMESTAMP_FIELD_NAME;
    System.arraycopy(resultNames, 0, newResultNames, 1, resultNames.length);
    return newResultNames;
  }

  /**
   * Gets the data types of each of the columns produced for a single run.
   * This method should really be static.
   *
   * @return an array containing objects of the type of each column. The 
   * objects should be Strings, or Doubles.
   */
  public Object [] getResultTypes() {

    Object [] resultTypes = m_splitEvaluator.getResultTypes();
    // Add in the types of our extra Result fields
    Object [] newResultTypes = new Object [resultTypes.length + 1];
    newResultTypes[0] = new Double(0);
    System.arraycopy(resultTypes, 0, newResultTypes, 1, resultTypes.length);
    return newResultTypes;
  }

  /**
   * Gets a description of the internal settings of the result
   * producer, sufficient for distinguishing a ResultProducer
   * instance from another with different settings (ignoring
   * those settings set through this interface). For example,
   * a cross-validation ResultProducer may have a setting for the
   * number of folds. For a given state, the results produced should
   * be compatible. Typically if a ResultProducer is an OptionHandler,
   * this string will represent the command line arguments required
   * to set the ResultProducer to that state.
   *
   * @return the description of the ResultProducer state, or null
   * if no state is defined
   */
  public String getCompatibilityState() {

    String result = "-X " + m_numFolds + " "; 
    if (m_splitEvaluator == null) {
      result += "<null SplitEvaluator>";
    } else {
      result += "-W " + m_splitEvaluator.getClass().getName();
    }
    return result + " --";
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String outputFileTipText() {
    return "Set the destination for saving raw output. If the rawOutput "
      +"option is selected, then output from the splitEvaluator for "
      +"individual folds is saved. If the destination is a directory, "
      +"then each output is saved to an individual gzip file; if the "
      +"destination is a file, then each output is saved as an entry "
      +"in a zip file.";
  }

  /**
   * Get the value of OutputFile.
   *
   * @return Value of OutputFile.
   */
  public File getOutputFile() {
    
    return m_outputFile;
  }
  
  /**
   * Set the value of OutputFile.
   *
   * @param newOutputFile Value to assign to OutputFile.
   */
  public void setOutputFile(File newOutputFile) {
    
    m_outputFile = newOutputFile;
  }  

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String numFoldsTipText() {
    return "Number of folds to use in cross validation.";
  }

  /**
   * Get the value of NumFolds.
   *
   * @return Value of NumFolds.
   */
  public int getNumFolds() {
    
    return m_numFolds;
  }

  
  /**
   * Set the value of NumFolds.
   *
   * @param newNumFolds Value to assign to NumFolds.
   */
  public void setNumFolds(int newNumFolds) {
    
    m_numFolds = newNumFolds;
  }

  /** Set/get the starting fold */
  public void setStartingFold(int startingFold) {
    m_startingFold = startingFold;
  }
  public int getStartingFold() {
    return m_startingFold;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String plotPointsTipText() {
    return "A list of recall levels separated by commas or spaces. ";
  }
  
  /**
   * Get the value of PlotPoints.
   *
   * @return Value of PlotPoints.
   */
  public String getPlotPoints() {
    StringBuffer buf = new StringBuffer();
    if (m_plotPoints != null) 
      for (int i=0; i < m_plotPoints.length; i++) {
	buf.append(m_plotPoints[i]);
	if (i != (m_plotPoints.length -1)) 
	  buf.append(" ");
      }
    return buf.toString();
  }
  
  /**
   * Set the value of PlotPoints.
   *
   * @param plotPoints Value to assign to
   * PlotPoints.
   */
  public void setPlotPoints(String plotPoints) {
    m_plotPoints = parsePlotPoints(plotPoints);
  }
  
  /** 
   * Parse a string of doubles separated by commas or spaces into a sorted array of doubles
   */
  protected double[] parsePlotPoints(String plotPoints) {
    StringTokenizer tokenizer = new StringTokenizer(plotPoints," ,\t");
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
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String rawOutputTipText() {
    return "Save raw output (useful for debugging). If set, then output is "
      +"sent to the destination specified by outputFile";
  }

  /**
   * Get if raw split evaluator output is to be saved
   * @return true if raw split evalutor output is to be saved
   */
  public boolean getRawOutput() {
    return m_debugOutput;
  }
  
  /**
   * Set to true if raw split evaluator output is to be saved
   * @param d true if output is to be saved
   */
  public void setRawOutput(boolean d) {
    m_debugOutput = d;
  }

  /** Set the mode of creating folds
   * @param mode stratified or random
   */
  public void setFoldCreationMode(SelectedTag mode) {
    if (mode.getTags() == TAGS_FOLD_CREATION_MODE) {
      m_foldCreationMode = mode.getSelectedTag().getID();
    }
  }

  /**
   * return the fold creation mode
   * @return one of  stratified or random
   */
  public SelectedTag getFoldCreationMode() {
    return new SelectedTag(m_foldCreationMode, TAGS_FOLD_CREATION_MODE);
  }
  

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String splitEvaluatorTipText() {
    return "The clusterer to apply to the cross validation folds.";
  }
 
  /**
   * Get the SplitEvaluator.
   *
   * @return the SplitEvaluator.
   */
  public SplitEvaluator getSplitEvaluator() {
    return m_splitEvaluator;
  }
  
  /**
   * Set the SplitEvaluator.
   *
   * @param newSplitEvaluator new SplitEvaluator to use.
   */
  public void setSplitEvaluator(SplitEvaluator newSplitEvaluator) {
    m_splitEvaluator = newSplitEvaluator;
    m_splitEvaluator.setAdditionalMeasures(m_additionalMeasures);
  }

  /**
   * Returns an enumeration describing the available options..
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(7);

    newVector.addElement(new Option(
	     "\tThe number of folds to use for the cross-validation.\n"
	      +"\t(default 10)", 
	     "X", 1, 
	     "-X <number of folds>"));

    newVector.addElement(new Option(
	     "Save raw split evaluator output.",
	     "D",0,"-D"));

    newVector.addElement(new Option(
	     "\tThe filename where raw output will be stored.\n"
	     +"\tIf a directory name is specified then then individual\n"
	     +"\toutputs will be gzipped, otherwise all output will be\n"
	     +"\tzipped to the named file. Use in conjuction with -D."
	     +"\t(default splitEvalutorOut.zip)", 
	     "O", 1, 
	     "-O <file/directory name/path>"));

    newVector.addElement(new Option(
	     "\tThe full class name of a SplitEvaluator.\n"
	      +"\teg: weka.experiment.ClustererSplitEvaluator", 
	     "W", 1, 
	     "-W <class name>"));

    if ((m_splitEvaluator != null) &&
	(m_splitEvaluator instanceof OptionHandler)) {
      newVector.addElement(new Option(
	     "",
	     "", 0, "\nOptions specific to split evaluator "
	     + m_splitEvaluator.getClass().getName() + ":"));
      Enumeration en = ((OptionHandler)m_splitEvaluator).listOptions();
      while (en.hasMoreElements()) {
	newVector.addElement(en.nextElement());
      }
    }
    return newVector.elements();
  }

  /**
   * Parses a given list of options. Valid options are:<p>
   *
   * -X num_folds <br>
   * The number of folds to use for the cross-validation. <p>
   *
   * -D <br>
   * Specify that raw split evaluator output is to be saved. <p>
   *
   * -O file/directory name <br>
   * Specify the file or directory to which raw split evaluator output
   * is to be saved. If a directory is specified, then each output string
   * is saved as an individual gzip file. If a file is specified, then
   * each output string is saved as an entry in a zip file. <p>
   *
   * -W classname <br>
   * Specify the full class name of the split evaluator. <p>
   *
   * All option after -- will be passed to the split evaluator.
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {
    
    setRawOutput(Utils.getFlag('D', options));

    String fName = Utils.getOption('O', options);
    if (fName.length() != 0) {
      setOutputFile(new File(fName));
    }

    String numFolds = Utils.getOption('X', options);
    if (numFolds.length() != 0) {
      setNumFolds(Integer.parseInt(numFolds));
    } else {
      setNumFolds(10);
    }

    String seName = Utils.getOption('W', options);
    if (seName.length() == 0) {
      throw new Exception("A SplitEvaluator must be specified with"
			  + " the -W option.");
    }
    // Do it first without options, so if an exception is thrown during
    // the option setting, listOptions will contain options for the actual
    // SE.
    setSplitEvaluator((LinkageSplitEvaluator)Utils.forName(
		      SplitEvaluator.class,
		      seName,
		      null));
    if (getSplitEvaluator() instanceof OptionHandler) {
      ((OptionHandler) getSplitEvaluator())
	.setOptions(Utils.partitionOptions(options));
    }
  }

  /**
   * Gets the current settings of the result producer.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {

    String [] seOptions = new String [0];
    if ((m_splitEvaluator != null) && 
	(m_splitEvaluator instanceof OptionHandler)) {
      seOptions = ((OptionHandler)m_splitEvaluator).getOptions();
    }
    
    String [] options = new String [seOptions.length + 10];
    int current = 0;

    switch (m_foldCreationMode) {
    case FOLD_CREATION_MODE_STRATIFIED:
      options[current++] = "-Stratified";
      break;
    case FOLD_CREATION_MODE_RANDOM:
      options[current++] = "-Random";
      break;
    }

    options[current++] = "-X";
    options[current++] = "" + getNumFolds();

    if (getRawOutput()) {
      options[current++] = "-D";
    }

    options[current++] = "-O"; 
    options[current++] = getOutputFile().getName();
    
    options[current++] = "-P";
    options[current++] = getPlotPoints();

    if (getSplitEvaluator() != null) {
      options[current++] = "-W";
      options[current++] = getSplitEvaluator().getClass().getName();
    }
    options[current++] = "--";

    System.arraycopy(seOptions, 0, options, current, 
		     seOptions.length);
    current += seOptions.length;
    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }

  /**
   * Gets a text descrption of the result producer.
   *
   * @return a text description of the result producer.
   */
  public String toString() {

    String result = "LinkagePRCurveCVResultProducer: ";
    result += getCompatibilityState();
    if (m_instances == null) {
      result += ": <null Instances>";
    } else {
      result += ": " +  Utils.backQuoteChars(m_instances.relationName());
    }
    return result;
  }
    
  // Quick test of timestamp
  public static void main(String [] args) {
    LinkagePRCurveCVResultProducer rp = new LinkagePRCurveCVResultProducer();
    rp.setPlotPoints(args[0]);
    System.out.println(rp.getPlotPoints());
  }
}
