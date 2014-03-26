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
 *    TrainingSetListener.java
 *    Copyright (C) 2002 Mark Hall
 *
 */

package weka.gui.beans;

import java.util.EventListener;

/**
 * Interface to something that can accept and process training set events
 *
 * @author <a href="mailto:mhall@cs.waikato.ac.nz">Mark Hall</a>
 * @version $Revision: 1.1.1.1 $
 */
public interface TrainingSetListener extends EventListener {

  /**
   * Accept and process a training set
   *
   * @param e a <code>TrainingSetEvent</code> value
   */
  void acceptTrainingSet(TrainingSetEvent e);
}
