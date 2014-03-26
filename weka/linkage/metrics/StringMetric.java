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
 *    StringMetric.java
 *    Copyright (C) 2003 Mikhail Bilenko
 *
 */


package weka.linkage.metrics;

import weka.core.*;

/**
 * An abstract class that returns a measure of similarity between strings
 *
 * @author Mikhail Bilenko
 */

public abstract class StringMetric implements Cloneable {

  /** We can have different ways of converting from distance to similarity  */
  public static final int CONVERSION_LAPLACIAN = 1;
  public static final int CONVERSION_UNIT = 2;
  public static final int CONVERSION_EXPONENTIAL = 4;
  public static final Tag[] TAGS_CONVERSION = {
    new Tag(CONVERSION_UNIT, "similarity = 1-distance"),
    new Tag(CONVERSION_LAPLACIAN, "similarity=1/(1+distance)"),
    new Tag(CONVERSION_EXPONENTIAL, "similarity=exp(-distance)")
      };
  /** The method of converting, by default laplacian */
  protected int m_conversionType = CONVERSION_EXPONENTIAL;

  /** Compute a measure of distance between two strings
   * @param s1 first string
   * @param s2 second string
   * @returns distance between two strings
   */
  public abstract double distance(String s1, String s2) throws Exception;

  
  /** Returns a similarity estimate between two strings. Similarity is obtained by
   * inverting the distance value using one of three methods:
   * CONVERSION_LAPLACIAN, CONVERSION_EXPONENTIAL, CONVERSION_UNIT.
   * @param string1 First string.
   * @param string2 Second string.
   * @exception Exception if similarity could not be estimated.
   */
  public double similarity(String string1, String string2) throws Exception {
    switch (m_conversionType) {
    case CONVERSION_LAPLACIAN: 
      return 1 / (1 + distance(string1, string2));
    case CONVERSION_UNIT:
      return 2 * (1 - distance(string1, string2));
    case CONVERSION_EXPONENTIAL:
      return Math.exp(-distance(string1, string2));
    default:
      throw new Exception ("Unknown distance to similarity conversion method");
    }
  }


  /** The computation of a metric can be either based on distance, or on similarity
   * @returns true if the underlying metric computes distance, false if similarity
   */
  public abstract boolean isDistanceBased();
  
  /** Create a copy of this metric */
  public abstract Object clone();

  /** Set the type of similarity to distance conversion. Values other
   * than CONVERSION_LAPLACIAN, CONVERSION_UNIT, or CONVERSION_EXPONENTIAL will be ignored
   * @param type type of the similarity to distance conversion to use  */
  public void setConversionType(SelectedTag conversionType) {
    if (conversionType.getTags() == TAGS_CONVERSION) {
      m_conversionType = conversionType.getSelectedTag().getID();
    }
  }
  /** return the type of similarity to distance conversion
   * @return one of CONVERSION_LAPLACIAN, CONVERSION_UNIT, or CONVERSION_EXPONENTIAL  */
  public SelectedTag getConversionType() {
    return new SelectedTag(m_conversionType, TAGS_CONVERSION);
  }


  /**
   * Creates a new instance of a metric given it's class name and
   * (optional) arguments to pass to it's setOptions method. If the
   * classifier implements OptionHandler and the options parameter is
   * non-null, the classifier will have it's options set.
   *
   * @param metricName the fully qualified class name of the metric 
   * @param options an array of options suitable for passing to setOptions. May
   * be null.
   * @return the newly created metric ready for use.
   * @exception Exception if the metric  name is invalid, or the options
   * supplied are not acceptable to the metric 
   */
  public static StringMetric forName(String metricName,
				     String [] options) throws Exception {
    return (StringMetric)Utils.forName(StringMetric.class,
				       metricName,
				       options);
  }
      
}

