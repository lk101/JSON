package org.javascript;

import java.util.Map;

/**
 * Define the Reference class.
 */
class Reference {
	private final String key;
	private final Object refer;
	
	/**
	 * Constructor.
	 * @param value
	 * @param key
	 */
	public Reference(Object value, Object key) {
		this.key   = String.valueOf(key);
		this.refer = value;
	}
	
	/**
	 * Get the constructor of reference.
	 * @return
	 */
	public String constructor() {
		if(refer instanceof Iterable) {
			return "Array";
		} else if(refer instanceof Map) {
			return "Object";
		}
		return refer.getClass().getName();
	}
	
	/**
	 * Get the key of reference.
	 * @return
	 */
	public String key() {
		return key;
	}
	
	/**
	 * Tests if circle reference occur.
	 * @param obj
	 * @return
	 */
	public boolean circle(Object obj) {
		return refer == obj;
	}
}
