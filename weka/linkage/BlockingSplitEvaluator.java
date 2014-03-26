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
 *    BlockingSplitEvaluator.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage;

import java.io.*;
import java.util.*;

import weka.core.*;
import weka.experiment.*;
import weka.linkage.blocking.*;


/**
 * A SplitEvaluator that produces results for a blocking scheme
 * on a nominal class attribute.
 *
 * -W classname <br>
 * Specify the full class name of the blocking scheme to evaluate. <p>
 *
 * @author Beena Kamath
 * @version $Revision: 1.1.1.1 $
 */
public class BlockingSplitEvaluator implements SplitEvaluator, 
  OptionHandler {
  
  /** The blocking scheme used for evaluation */
  protected Blocking m_blocking = new LearnableBlocking();

  /** Holds the statistics for the most recent application of the blocking */
  protected String m_result = null;

  /** The blocking options (if any) */
  protected String m_blockingOptions = "";

  /** The blocking version */
  protected String m_blockingVersion = "";

  /** The length of a key */
  private static final int KEY_SIZE = 3;

  /** The length of a result */
  private static final int RESULT_SIZE = 21;

  /**
   * No args constructor.
   */
  public BlockingSplitEvaluator() {

    updateOptions();
  }

  /**
   * Returns a string describing this split evaluator
   * @return a description of the split evaluator suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return " A SplitEvaluator that produces results for a blocking  "
      +"scheme on a nominal class attribute.";
  }

  /** Does nothing, since blocking evaluation does not allow additional measures */
  public void setAdditionalMeasures(String [] additionalMeasures){}
  
  /**
   * Returns an enumeration describing the available options..
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration listOptions() {

    Vector newVector = new Vector(2);

    newVector.addElement(new Option(
                                    "\tThe full class name of blocking scheme.\n"
                                    +"\teg: weka.linkage.blocking.Blocking", 
                                    "W", 1, 
                                    "-W <class name>"));

    if ((m_blocking != null) &&
	(m_blocking instanceof OptionHandler)) {
      newVector.addElement(new Option(
                                      "",
                                      "", 0, "\nOptions specific to blocking "
                                      + m_blocking.getClass().getName() + ":"));
      Enumeration enu = ((OptionHandler)m_blocking).listOptions();
      while (enu.hasMoreElements()) {
	newVector.addElement(enu.nextElement());
      }
    }
    return newVector.elements();
  }

  /**
   * Parses a given list of options. Valid options are:<p>
   *
   * -W classname <br>
   * Specify the full class name of the blocking scheme to evaluate. <p>
   *
   * All option after -- will be passed to the blocking.
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {
    String cName = Utils.getOption('W', options);
    if (cName.length() == 0) {
      throw new Exception("A Blocking scheme must be specified with"
			  + " the -W option.");
    }
    // Do it first without options, so if an exception is thrown during
    // the option setting, listOptions will contain options for the actual
    // Blocking.
    setBlocking(Blocking.forName(cName, null));
    if (getBlocking() instanceof OptionHandler) {
      ((OptionHandler) getBlocking())
	.setOptions(Utils.partitionOptions(options));
      updateOptions();
    }
  }

  /**
   * Gets the current settings of the Blocking scheme.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {

    String [] blockingOptions = new String [0];
    if ((m_blocking != null) && 
	(m_blocking instanceof OptionHandler)) {
      blockingOptions = ((OptionHandler)m_blocking).getOptions();
    }
    
    String [] options = new String [blockingOptions.length + 5];
    int current = 0;

    if (getBlocking() != null) {
      options[current++] = "-W";
      options[current++] = getBlocking().getClass().getName();
    }
    options[current++] = "--";

    System.arraycopy(blockingOptions, 0, options, current, 
		     blockingOptions.length);
    current += blockingOptions.length;
    while (current < options.length) {
      options[current++] = "";
    }
    return options;
  }


  /**
   * Gets the data types of each of the key columns produced for a single run.
   * The number of key fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing objects of the type of each key column. The 
   * objects should be Strings, or Doubles.
   */
  public Object [] getKeyTypes() {

    Object [] keyTypes = new Object[KEY_SIZE];
    keyTypes[0] = "";
    keyTypes[1] = "";
    keyTypes[2] = "";
    return keyTypes;
  }

  /**
   * Gets the names of each of the key columns produced for a single run.
   * The number of key fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing the name of each key column
   */
  public String [] getKeyNames() {

    String [] keyNames = new String[KEY_SIZE];
    keyNames[0] = "Scheme";
    keyNames[1] = "Scheme_options";
    keyNames[2] = "Scheme_version_ID";
    return keyNames;
  }

  /**
   * Gets the key describing the current SplitEvaluator. For example
   * This may contain the name of the blocking used for blocking
   * predictive evaluation. The number of key fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array of objects containing the key.
   */
  public Object [] getKey(){

    Object [] key = new Object[KEY_SIZE];
    key[0] = m_blocking.getClass().getName();
    key[1] = m_blockingOptions;
    key[2] = m_blockingVersion;
    return key;
  }

  /**
   * Gets the data types of each of the result columns produced for a 
   * single run. The number of result fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing objects of the type of each result column. 
   * The objects should be Strings, or Doubles.
   */
  public Object [] getResultTypes() {
    int overall_length = RESULT_SIZE;
    Object [] resultTypes = new Object[overall_length];
    Double doub = new Double(0);
    int current = 0;
    resultTypes[current++] = doub;

    // Accuracy stats
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    // Dupe density stats
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;    

    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    resultTypes[current++] = doub;
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    // Timing stats
    resultTypes[current++] = doub;
    resultTypes[current++] = doub;

    if (current != overall_length) {
      throw new Error("ResultTypes didn't fit RESULT_SIZE");
    }
    return resultTypes;
  }

  /**
   * Gets the names of each of the result columns produced for a single run.
   * The number of result fields must be constant
   * for a given SplitEvaluator.
   *
   * @return an array containing the name of each result column
   */
  public String [] getResultNames() {
    int overall_length = RESULT_SIZE;
    String [] resultNames = new String[overall_length];
    int current = 0;
    resultNames[current++] = "Number_of_instances";

    // Accuracy stats
    resultNames[current++] = "pRecall";
    resultNames[current++] = "pPrecision";
    resultNames[current++] = "pReductionRatio"; 
    resultNames[current++] = "Fmeasure";

    // Dupe density stats
    resultNames[current++] = "TotalPairsTrain";
    resultNames[current++] = "PotentialDupePairsTrain";
    resultNames[current++] = "ActualDupePairsTrain";
    resultNames[current++] = "PotentialNonDupePairsTrain";
    resultNames[current++] = "ActualNonDupePairsTrain";
    resultNames[current++] = "DupeNonDupeRatioTrain";
    resultNames[current++] = "DupeOveralProportionTrain";
    
    resultNames[current++] = "TotalPairsTest";
    resultNames[current++] = "DupePairsTest";
    resultNames[current++] = "DupeNonDupeRatioTest";
    resultNames[current++] = "ProportionBadPairsBlocked";

    resultNames[current++] = "NumGoodPairs";
    resultNames[current++] = "NumBlockedPairs";
    resultNames[current++] = "NumBlockers";
    

    // Timing stats
    resultNames[current++] = "Time_training";
    resultNames[current++] = "Time_testing";

    if (current != overall_length) {
      throw new Error("ResultNames didn't fit RESULT_SIZE");
    }
    return resultNames;
  }

  /**
   * Gets the results for the supplied train and test datasets.
   *
   * @param train the training Instances.
   * @param test the testing Instances.
   * @return the raw results stored in an array. The objects stored in
   * the array are object arrays, containing actual P/R/FM values for each point
   * @exception Exception if a problem occurs while getting the results
   */
  public Object [] getResult(Instances trainData, Instances testData) 
    throws Exception {
    
    if (trainData.classAttribute().type() != Attribute.NOMINAL) {
      throw new Exception("Class attribute is not nominal!");
    }
    if (m_blocking == null) {
      throw new Exception("No blocking has been specified");
    }

    BlockingEvaluation eval = new BlockingEvaluation();
    eval.trainBlocking(m_blocking, trainData, testData);
    System.out.println("Evaluator trained");
    ArrayList rawResultList = eval.evaluateModel(m_blocking, testData);
    System.out.println("Evaluator tested");
    System.out.println("Result size is " + rawResultList.size());
    
    return rawResultList.toArray();
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String blockingTipText() {
    return "The blocking scheme to use.";
  }

  /**
   * Get the value of Blocking.
   *
   * @return Value of Blocking.
   */
  public Blocking getBlocking() {
    return m_blocking;
  }
  
  /**
   * Sets the deduper.
   *
   * @param newBlocking the new deduper to use.
   */
  public void setBlocking(Blocking newBlocking) {
    m_blocking = newBlocking;
    updateOptions();
  }
  
  /**
   * Updates the options that the current deduper is using.
   */
  protected void updateOptions() {
    if (m_blocking instanceof OptionHandler) {
      m_blockingOptions = Utils.joinOptions(((OptionHandler)m_blocking)
                                            .getOptions());
    } else {
      m_blockingOptions = "";
    }
    if (m_blocking instanceof Serializable) {
      ObjectStreamClass obs = ObjectStreamClass.lookup(m_blocking
						       .getClass());
      m_blockingVersion = "" + obs.getSerialVersionUID();
    } else {
      m_blockingVersion = "";
    }
  }

  /**
   * Set the Blocking to use, given it's class name. A new blocking will be
   * instantiated.
   *
   * @param newBlocking the Blocking class name.
   * @exception Exception if the class name is invalid.
   */
  public void setBlockingName(String newBlockingName) throws Exception {
    try {
      setBlocking((Blocking)Class.forName(newBlockingName)
                  .newInstance());
    } catch (Exception ex) {
      throw new Exception("Can't find Blocking with class name: "
			  + newBlockingName);
    }
  }

  /**
   * Gets the raw output from the blocking
   * @return the raw output from the blocking
   */
  public String getRawResultOutput() {
    StringBuffer result = new StringBuffer();

    if (m_blocking == null) {
      return "<null> blocking";
    }
    result.append(toString());
    result.append("Blocking model: \n"+m_blocking.toString()+'\n');

    // append the performance statistics
    if (m_result != null) {
      result.append(m_result);
    }
    return result.toString();
  }

  /**
   * Returns a text description of the split evaluator.
   *
   * @return a text description of the split evaluator.
   */
  public String toString() {

    String result = "BlockingSplitEvaluator: ";
    if (m_blocking == null) {
      return result + "<null> blocking";
    }
    return result + m_blocking.getClass().getName() + " " 
      + m_blockingOptions + "(version " + m_blockingVersion + ")";
  }
} // BlockingSplitEvaluator
