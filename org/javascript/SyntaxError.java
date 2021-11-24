package org.javascript;

/**
 * Define the java script syntax error class.
 */
public class SyntaxError extends RuntimeException {
	private static final long serialVersionUID = -1341714875236296812L;

	/**
	 * Constructor of end unexpected.
	 */
	SyntaxError() {
		super(getEndMessage());
	}
	
	/**
	 * Get the end unexpected message.
	 * @return
	 */
	private static String getEndMessage() {
		return "Unexpected end of JSON input";
	}
	
	/**
	 * Constructor of type unexpected.
	 * @param type
	 * @param pos
	 */
	SyntaxError(String type, int pos) {
		super(getTypeMessage(type, pos));
	}
	
	/**
	 * Get the type unexpected message.
	 * @param type
	 * @param pos
	 * @return
	 */
	private static String getTypeMessage(String type, int pos) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("Unexpected ");
		sb.append(type);
		sb.append(" in JSON at position ");
		sb.append(pos);
		
		return sb.toString();
	}
	
	/**
	 * Constructor of token unexpected.
	 * @param c
	 * @param pos
	 */
	SyntaxError(char c, int pos) {
		super(getTokenMessage(c, pos));
	}
	
	/**
	 * Get the token unexpected message.
	 * @param c
	 * @param pos
	 * @return
	 */
	private static String getTokenMessage(char c, int pos) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("Unexpected token ");
		sb.append(c);
		sb.append(" in JSON at position ");
		sb.append(pos);
		
		return sb.toString();
	}
}
