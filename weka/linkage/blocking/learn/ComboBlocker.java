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
 *    ComboBlocker.java
 *    Copyright (C) 2005 Beena Kamath
 *
 */


package weka.linkage.blocking.learn;

import java.util.*;
import java.io.Serializable;
import weka.core.*;
import weka.linkage.*;

/**
 * This class takes a set of records, creates blocks based on the
 * criterion of having the same first three characters. 
 *
 * @author Beena Kamath
 */

public class ComboBlocker extends Blocker
  implements OptionHandler, Serializable, Cloneable {

  /** The array of blockers */
  protected Blocker[] m_blockers = null;

  /** Their indeces in some list */
  protected int[] m_idxs = null; 
  

  boolean m_overlapping = false; 

  /** Initializations */
  public ComboBlocker() {
  }

  /** Blocks may be disjoint or overlapping */
  public boolean blocksOverlap() {
    return m_overlapping;
  }

  public ComboBlocker(Blocker[] blockers) {
    setBlockers(blockers); 
  }

  public ComboBlocker(int[] idxs, List<Blocker> blockerList) {
    m_blockers = new Blocker[idxs.length];
    m_overlapping = false; 
    
    for (int i = 0; i < idxs.length; i++) {
      m_blockers[i] = blockerList.get(idxs[i]);
      m_overlapping = m_overlapping || m_blockers[i].blocksOverlap(); 
    }

    m_idxs = idxs; 
  }

  /** Free up unnecessary memory */
  public void resetBlocker() {
    m_blockingMap = null;
    for (int i = 0; i < m_blockers.length; i++) {
      m_blockers[i].resetBlocker();
    }
  }

  /** Return a copy of this Blocker */
  public Blocker clone() {
    ComboBlocker blocker = new ComboBlocker();
    return blocker;
  }


  /** Set the list of blockers */
  public void setBlockers(Blocker[] blockers) {
    m_blockers = new Blocker[blockers.length];
    m_overlapping = true;
        
    for (int i = 0; i < blockers.length; i++) {
      m_blockers[i] = blockers[i];
      m_overlapping = m_overlapping && blockers[i].blocksOverlap(); 
    }
  }


  /** Given the data, construct the blocking */
  public void blockData(Instances instances, boolean redBlue) {
    m_redBlue = redBlue; 
    m_instances = instances;
    int numInstances = instances.numInstances();
    m_blockingMap = new BlockingMap(numInstances,
                                    blocksOverlap(), m_redBlue);

    // if we have some TF-IDF based blockers, need to call blockData
    // on them first
    for (int i = 0; i < m_blockers.length; i++) {
      if (m_blockers[i] instanceof TFIDFBlocker) {
        m_blockers[i].blockData(instances, redBlue);
      }
    } 
    
    // for all instances
    for (int idx = 0; idx < numInstances; idx++) {
      Object[] blocks = getBlocks(instances.instance(idx)); 
      m_blockingMap.putInstanceBlocks(idx, blocks);

      for (Object blockValue : blocks) { 
        m_blockingMap.putInstanceIdx(idx, blockValue);
      }
    }
    m_blockingMap.createPotentialPairs(instances.numInstances());
  }

  
  /** Return the list of blocks for a given instance */
  public Object[] getBlocks (Instance instance) {
    Object[] blocks = m_blockers[0].getBlocks(instance);
    if (blocks == null) {
      return new Object[0]; 
    }

    for (int i = 1; i < m_blockers.length; i++) {
      Object[] currBlocks = m_blockers[i].getBlocks(instance);
      Object[] newBlocks = new Object[blocks.length * currBlocks.length];
      int blockIdx = 0; 
      for (int oldIdx = 0; oldIdx < blocks.length; oldIdx++) {
        for (int newIdx = 0; newIdx < currBlocks.length; newIdx++) {
          newBlocks[blockIdx++] = blocks[oldIdx] +"___"+ currBlocks[newIdx];
        }
      }
      blocks = newBlocks; 
    }

    return blocks; 
  } 


  /** Given two instances, do they fall into the same block? */
  public boolean sameBlock(Instance instance1, Instance instance2) {
    for (int i = 0; i < m_blockers.length; i++) {
      if (!m_blockers[i].sameBlock(instance1, instance2)) { 
        return false;
      }
    }
    return true; 
  }
  


  /**
   * Gets the current settings of the blocker.
   *
   * @return an array of strings suitable for passing to setOptions()
   */
  public String [] getOptions() {
    String [] options = new String [0];

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
    StringBuffer buff = new StringBuffer();

    for (int i = 0; i < m_blockers.length; i++) {
      buff.append(" -*- " + m_blockers[i].toString()); 
    }
    return buff.toString();
  }
}
