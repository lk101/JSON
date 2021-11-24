package org.javascript;

import java.util.Iterator;
import java.util.StringJoiner;

/**
 * Define the TypeError class.
 */
public class TypeError extends RuntimeException {
	private static final long serialVersionUID = 8681054737874509637L;
	
	/**
	 * Constructor.
	 * @param type
	 */
	TypeError(String type) {
		super("Do not know how to serialize a " + type);
	}
	
	/**
	 * Constructor.
	 * @param list
	 */
	TypeError(Iterable<Reference> list) {
		super(getReferenceMessage(list));
	}
	
	/**
	 * Get the references message.
	 * @param list
	 * @return
	 */
	private static String getReferenceMessage(Iterable<Reference> list) {
		StringJoiner sj = new StringJoiner("\n    |     property ",
			"Converting circular structure to JSON\n    --> starting at ",
			"\n    --- property ");
		Reference last = null;
		boolean first = true;
		Iterator<Reference> iter = list.iterator();
		while(iter.hasNext()) {
			Reference refer = iter.next();
			if(first) {
				first = false;
				sj.add("object with constructor " + encode(refer.constructor()));
			} else if(iter.hasNext()) {
				sj.add(encode(refer.key()) + " -> object with constructor "
						+ encode(refer.constructor()));
			} else {
				last = refer;
			}
		}
		return sj.toString() + encode(last.key()) + " closes the circle";
	}
	
	/**
	 * Encode the key.
	 * @param key
	 * @return
	 */
	private static String encode(String key) {
		return "'" + key + "'";
	}
}
