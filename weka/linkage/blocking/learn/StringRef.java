package weka.linkage.blocking.learn;

import java.io.*;
import weka.linkage.metrics.HashMapVector; 

/** A simple data structure for storing a reference to a document file
 *  that includes information on the length of its document vector.
 *  The goal is to have a lightweight object to store in an inverted index
 *  without having to store an entire Document object.
 *
 * @author Ray Mooney
 */

public class StringRef {
  /** The referenced string. */
  public String string = null;

  /** The corresponding HashMapVector */
  public HashMapVector vector = null;

  /** The length of the corresponding Document vector. */
  public double length = 0.0;

  /** Index of the instance it came from */
  public int idx = -1;

  /** Class value of the instance it came from */
  public double classValue = -1; 

  public StringRef(int i, double classVal,
                   String str, HashMapVector vec, double l) {
    idx = i;
    classValue = classVal; 
    string = str;
    vector = vec;
    length = l;
  }

  /** Create a reference to this document, initializing its length to 0 */
  public StringRef(int i, double classVal, String str, HashMapVector vec) {
    this(i, classVal, str, vec, 0.0);
  }

  public String toString() {
    return string;
  }
}
