package org.javascript;

/**
 * Define the Walker interface.
 */
public interface Processor {
	/**
	 * Revive the object.
	 * @param that
	 * @param key
	 * @param value
	 * @return
	 */
	Object process(Object that, String key, Object value);
}
