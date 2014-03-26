package weka.linkage.blocking.learn;

/** A lightweight object for storing information about an occurrence of a token (a.k.a word, term)
 * in a Document.
 *
 * @author Ray Mooney
 */

public class TokenStrOccurrence {
    /** A reference to the Document where it occurs */
    public StringRef stringRef = null;
    /** The number of times it occurs in the Document */
    public int count = 0;

    /** Create an occurrence with these values */
    public TokenStrOccurrence(StringRef strRef, int cnt) {
	stringRef = strRef;
	count = cnt;
    }
}
