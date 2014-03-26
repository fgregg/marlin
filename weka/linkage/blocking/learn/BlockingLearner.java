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
 *    BlockingLearner.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking.learn;

import weka.core.Instances;
import weka.core.Utils;
import java.util.*;
import weka.linkage.*;

/**
 * An abstract class for a blocking criterion learner
 *
 * @author Beena Kamath
 */
public abstract class BlockingLearner implements Cloneable {
  
  /** Given the data, learn the blocking criteria 
  */
  public abstract ArrayList getBlockers(Blocker[] blockers, Instances data);

  public static BlockingLearner forName(String blockingLearnerName, String[] options) throws Exception {
    return (BlockingLearner)Utils.forName(BlockingLearner.class,
                                  blockingLearnerName,
                                  options);
  }

} 


















