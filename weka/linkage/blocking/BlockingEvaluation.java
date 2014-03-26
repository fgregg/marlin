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
 *    BlockingEvaluation.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */

package  weka.linkage.blocking;

import  java.util.*;
import  java.io.*;
import  weka.core.*;
import  weka.filters.Filter;
import  weka.filters.unsupervised.attribute.Remove;

import weka.linkage.*;

/**
 * Class for evaluating learnable blocking
 * borrowed from LinkageEvaluation
 *
 * @author  Beena Kamath
 */
public class BlockingEvaluation {

  
  /**
   * Returns a string describing this evaluator
   * @return a description of the evaluator suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {
    return " A linkage evaluator that evaluates results of running a "
      + "blocking experiment.";
  }

  /** A default constructor */
  public BlockingEvaluation () {
  }

  /** Train a deduper on the supplied data
   * @param deduper a deduper to train
   * @param data training data
   */
  public void trainBlocking(Blocking blocking, Instances trainingData,
                            Instances testData) throws Exception {
    blocking.learnBlocking(trainingData);
  } 

  /**
   * Evaluates the Blocking criteria learnt on a given set of test instances
   *
   * @exception Exception if model could not be evaluated successfully
   */
  public ArrayList evaluateModel (Blocking blocking,
                                  Instances testInstances) throws Exception {
    blocking.buildIndex(testInstances);
    return blocking.getStatistics();
  }
}
