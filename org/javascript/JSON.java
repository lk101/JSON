package org.javascript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TimeZone;

import jdk.nashorn.internal.runtime.regexp.JoniRegExp;
import jdk.nashorn.internal.runtime.regexp.RegExp;
import jdk.nashorn.internal.runtime.regexp.RegExpMatcher;

/**
 * Define the JSON class.
 */
public class JSON {
	public static final Object undefined = new Object();
	
	/**
	 * Define the CollectionProcesser class.
	 */
	private static class CollectionProcesser implements Processor {
		private boolean first = true;
		private Set<String> keys;
		
		/**
		 * Constructor.
		 * @param keys
		 */
		private CollectionProcesser(Collection<String> keys) {
			this.keys = new HashSet<>(keys);
		}
		
		@Override
		public Object process(Object that, String key, Object value) {
			if(first) {
				first = false;
				return value;
			}
			return !(that instanceof Map) || keys.contains(key) ? value : undefined;
		}
	}
	
	/**
	 * Define the complex structure parser.
	 * @param <T> The result.
	 */
	private static interface ComplexParser<T> {
		
		/**
		 * Get the next character.
		 * @param find
		 * @param json
		 * @param match
		 * @param begin
		 * @param base
		 * @return
		 */
		static int getNextChar(char find, String json,
				RegExpMatcher match, int begin, int base) {
			if(json.charAt(begin) == find) {
				throw new SyntaxError(find, base + begin);
			}
			char lastDeli  = 0;
			boolean escape = false, string = false;
			List<Character> delimiters = new ArrayList<>();
			for(int i = begin, len = json.length(); i < len; i++) {
				char c = json.charAt(i);
				if(escape) {
					escape = false;
				} else if(string) {
					escape = c == '\\';
					string = c != '"';
				} else if(c == '"') {
					string = true;
				} else if(c == '[' || c == '{') {
					delimiters.add(lastDeli = c);
				} else if(lastDeli != 0 && c == lastDeli + 2) {
					int last = delimiters.size() - 1;
					delimiters.remove(last);
					lastDeli = last > 0 ? delimiters.get(last - 1) : 0;
				} else if(lastDeli == 0 && c == find) {
					return match.search(base + i + 1) ?
						Math.min(len, match.start() - base) : len;
				}
			}
			return -1;
		}
		
		/**
		 * Get the next comma.
		 * @param json
		 * @param match 
		 * @param begin
		 * @param base 
		 * @return
		 */
		static int getNextComma(String json,
				RegExpMatcher match, int begin, int base) {
			return getNextChar(',', json, match, begin, base);
		}
		
		/**
		 * Substring and cut out the last not white character.
		 * @param json
		 * @param begin
		 * @param end
		 * @return
		 */
		static String sublast(String json, int begin, int end) {
			String str = end >= 0 ? json.substring(begin, end)
					   : json.substring(begin);
			str = str.trim();
			
			return str.substring(0, str.length() - 1).trim();
		}
		
		/**
		 * Append the element to container.
		 * @param container
		 * @param json
		 * @param match
		 * @param base
		 */
		void appendElement(T container, String json, RegExpMatcher match, int base);
		
		/**
		 * Parse and get the result.
		 * @param container
		 * @param beginning
		 * @param ending
		 * @param json
		 * @param match
		 * @param base
		 */
		default T parse(T container, char beginning, char ending,
				String json, RegExpMatcher match, int base) {
			char first = json.charAt(0);
			if(first != beginning) throw new SyntaxError(first, base);
			if(!match.search(base + 1)) throw new SyntaxError();
			
			int begin = match.start() - base, end, last = json.length() - 1;
			char lastc = json.charAt(last);
			if(begin == last && lastc == ending) return container;
			
			while((end = getNextComma(json, match, begin, base)) >= 0) {
				appendElement(container, sublast(json, begin, end), match, base + begin);
				
				begin = end;
			}
			if(begin > last) throw new SyntaxError();
			
			end = getNextChar(ending, json, match, begin, base);
			appendElement(container, sublast(json, begin, end), match, base + begin);
			if(end < 0) {
				throw new SyntaxError();
			} else if(end < last + 1) {
				throw new SyntaxError(json.charAt(end), base + end);
			}
			return container;
		}
	}
	
	private static final long MAX_4_DATE	= 253402300800000L;
	private static final long MAX_DATE		= 8640000000000000L;
	private static final long MIN_4_DATE	= -62167219200000L;
	private static final RegExp NATURAL_INT	= new JoniRegExp("^0|[1-9]\\d*$", "");
	private static final RegExp NON_WHITE	= new JoniRegExp("\\S", "");
	private static final RegExp NUMBER		= new JoniRegExp("[-]?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?", "");
	
	/**
	 * Parse the JSON string.
	 * @param serialized
	 * @return
	 */
	public static Object parse(String serialized) {
		if(serialized == null) return null;
		
		RegExpMatcher match = NON_WHITE.match(serialized);
		match.search(0);
		
		return parse(serialized.trim(), match, match.start());
	}
	
	/**
	 * Parse the part of JSON string
	 * @param json
	 * @param match 
	 * @param base
	 * @return
	 */
	private static Object parse(String json, RegExpMatcher match, int base) {
		if(json.isEmpty()) throw new SyntaxError();
		
		if("null".equals(json)) {
			return null;
		} else if("true".equals(json)) {
			return true;
		} else if("false".equals(json)) {
			return false;
		}
		Number num = parseNumber(json, base);
		if(num != null) return num;
		
		String str = parseString(json, base);
		if(str != null) return str;
		
		return json.charAt(0) == '[' ?
			parseArray(json, match, base) :
			parseObject(json, match, base);
	}
	
	/**
	 * Parse the specified JSON string to number.
	 * @param json
	 * @param base
	 * @return
	 */
	private static Number parseNumber(String json, int base) {
		RegExpMatcher match = NUMBER.match(json);
		if(!match.search(0) || match.start() != 0) return null;
		
		int pos = match.end();
		if(pos < json.length()) throw new SyntaxError(json.charAt(pos), pos);
		
		double dVal = Double.parseDouble(json);
		long   lVal = (long)dVal;
		int	   iVal = (int )dVal;
		if(dVal == iVal) return iVal;
		if(dVal == lVal) return lVal;
		
		return dVal;
	}
	
	/**
	 * Parse the specified JSON string to string.
	 * @param json
	 * @param base
	 * @return
	 */
	private static String parseString(String json, int base) {
		int len = json.length();
		if(len < 2 || json.charAt(0) != '"') return null;
		
		StringBuilder sb = new StringBuilder();
		boolean escape = false;
		int last = len - 1;
		for(int i = 1; i < last; i++) {
			char c = json.charAt(i);
			if(c == '\n' || c == '\r' || c == '\b' || c == '\t' || c == '\f') {
				throw new SyntaxError(c, base + i);
			} else if(escape) {
				i += parseEscape(sb, c, json, i, last, base);
				escape = false;
			} else if(c == '"') {
				throw new SyntaxError("string", base + i);
			} else if(c != '\\') {
				sb.append(c);
			} else {
				escape = true;
			}
		}
		if(json.charAt(last) != '"') {
			throw new SyntaxError();
		}
		return sb.toString();
	}
	
	/**
	 * Parse the escape.
	 * @param sb
	 * @param c
	 * @param json
	 * @param pos
	 * @param last
	 * @param base
	 */
	private static int parseEscape(StringBuilder sb, char c,
			String json, int pos, int last, int base) {
		switch(c) {
		case 'n': { sb.append('\n'); break; }
		case 'r': { sb.append('\r'); break; }
		case 't': { sb.append('\t'); break; }
		case 'b': { sb.append('\b'); break; }
		case 'f': { sb.append('\f'); break; }
		case '/':
		case '\\':
		case '"': { sb.append(c);	 break; }
		case 'u':
			sb.append(parseUnicode(json, pos, last, base));
			return 4;
		default:
			if(c == '-' || c >= '0' && c <= '9') {
				throw new SyntaxError("number", base + pos);
			}
			throw new SyntaxError(c, base + pos);
		}
		return 0;
	}
	
	/**
	 * Parse the unicode string.
	 * @param json
	 * @param bgn
	 * @param end
	 * @param base
	 * @return
	 */
	private static char parseUnicode(String json, int bgn, int end, int base) {
		if(bgn >= end - 4) throw new SyntaxError("string", base + end);
		
		char code = 0;
		for(int i = 0; i < 4; i++, code <<= 4) {
			char u = json.charAt(++bgn);
			if(u >= '0' && u <= '9') {
				code |= u - '0';
			} else if(u >= 'A' && u <= 'F') {
				code |= u - 'A' + 10;
			} else if(u >= 'a' && u <= 'f') {
				code |= u - 'a' + 10;
			} else {
				throw new SyntaxError(u, base + bgn);
			}
		}
		return code;
	}
	
	/**
	 * Parse the array.
	 * @param json
	 * @param match
	 * @param base
	 * @return
	 */
	private static List<?> parseArray(String json, RegExpMatcher match, int base) {
		ComplexParser<List<Object>> parser =
				new ComplexParser<List<Object>>() {
			@Override
			public void appendElement(List<Object> container,
					String json, RegExpMatcher match, int base) {
				container.add(JSON.parse(json, match, base));
			}
		};
		return parser.parse(new ArrayList<>(), '[', ']', json, match, base);
	}
	
	/**
	 * Parse the object.
	 * @param json
	 * @param match
	 * @param base
	 * @return
	 */
	private static Map<String, ?> parseObject(String json, RegExpMatcher match, int base) {
		ComplexParser<Map<String, Object>> parser =
				new ComplexParser<Map<String, Object>>() {
			@Override
			public void appendElement(Map<String, Object> container,
					String json, RegExpMatcher match, int base) {
				parsePair(container, json, match, base);
			}
		};
		return parser.parse(new LinkedHashMap<>(), '{', '}', json, match, base);
	}
	
	/**
	 * Parse the key-value pair.
	 * @param map
	 * @param json
	 * @param match
	 * @param base
	 */
	private static void parsePair(Map<String, Object> map,
			String json, RegExpMatcher match, int base) {
		int	   idx = ComplexParser.getNextChar(':', json, match, 0, base);
		String ele = ComplexParser.sublast(json, 0, idx);
		String key = parseString(ele, base);
		if(key == null) {
			RegExpMatcher numMatch = NUMBER.match(ele);
			if(numMatch.search(0) && numMatch.start() == 0) {
				throw new SyntaxError("number", base);
			} else {
				throw new SyntaxError(json.charAt(0), base);
			}
		}
		map.put(key, parse(json.substring(idx).trim(), match, base + idx));
	}
	
	/**
	 * Parse the JSON string.
	 * @param serialized
	 * @param proc
	 * @return
	 */
	public static Object parse(String serialized, Processor proc) {
		Object parsed = parse(serialized);
		if(proc != null) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("", parsed);
			Object value = walk(proc, map, "");
			
			return value == undefined ? null : value;
		}
		return parsed;
	}
	
	/**
	 * Walk and replace the properties.
	 * @param obj
	 * @param proc
	 * @return
	 */
	private static Object walk(Processor proc, Object obj, Object key) {
		Object value = getValue(obj, key);
		if(value instanceof List) {
			value = walkArray(proc, (List<?>)value);
		} else if(value instanceof Map) {
			value = walkMap(proc, (Map<?, ?>)value);
		}
		return proc.process(obj, String.valueOf(key), value);
	}
	
	/**
	 * Get the value.
	 * @param obj
	 * @param key
	 * @return
	 */
	private static Object getValue(Object obj, Object key) {
		if(obj instanceof List) {
			Integer index = getIndex(key);
			if(index == null) return null;
			
			return ((List<?>)obj).get(index);
		} else if(obj instanceof Map) {
			return ((Map<?, ?>)obj).get(key);
		}
		return null;
	}
	
	/**
	 * Get the index.
	 * @param key
	 * @return
	 */
	private static Integer getIndex(Object key) {
		if(key instanceof Integer) {
			return (int)key >= 0 ? (Integer)key : null;
		} else {
			String val = String.valueOf(key);
			return NATURAL_INT.match(val).search(0)
				? Integer.valueOf(val) : null;
		}
	}
	
	/**
	 * Walk the array.
	 * @param proc
	 * @param list
	 * @return
	 */
	private static List<Object> walkArray(Processor proc, List<?> list) {
		List<Object> array = new ArrayList<>();
		for(int i = 0, len = list.size(); i < len; i++) {
			Object value = walk(proc, list, i);
			array.add(value == undefined ? null : value);
		}
		return array;
	}
	
	/**
	 * Walk the map.
	 * @param proc
	 * @param src
	 * @return
	 */
	private static Map<String, Object> walkMap(Processor proc, Map<?, ?> src) {
		Map<String, Object> map = new LinkedHashMap<>();
		Iterator<?> iter = src.keySet().iterator();
		while(iter.hasNext()) {
			Object key = iter.next();
			Object val = walk(proc, src, key);
			if(val != undefined) map.put(String.valueOf(key), val);
		}
		return map;
	}
	
	/**
	 * Transform the java object to JSON string.
	 * @param value supported {@link java.lang.Number},
	 * {@link java.lang.String}, {@link java.lang.Boolean},
	 * {@link java.lang.Iterable}, {@link java.util.Map}.<br>
	 * The element of {@code Iterable} and the value of {@code Map} must be
	 * one of all above.<br>
	 * The key of {@code Map} must be {@link java.lang.String}.
	 * @return
	 */
	public static String stringify(Object value) {
		return stringify(value, (Processor)null, "");
	}
	
	/**
	 * Transform the java object to JSON string.
	 * @param value supported {@link java.lang.Number},
	 * {@link java.lang.String}, {@link java.lang.Boolean},
	 * {@link java.lang.Iterable}, {@link java.util.Map}.<br>
	 * The element of {@code Iterable} and the value of {@code Map} must be
	 * one of all above.<br>
	 * The key of {@code Map} must be {@link java.lang.String}.
	 * @param keys available key set.
	 * @return
	 */
	public static String stringify(Object value, Collection<String> keys) {
		Processor proc = keys == null ? null : new CollectionProcesser(keys);
		
		return stringify(value, proc, "");
	}
	
	/**
	 * Transform the java object to JSON string.
	 * @param value supported {@link java.lang.Number},
	 * {@link java.lang.String}, {@link java.lang.Boolean},
	 * {@link java.lang.Iterable}, {@link java.util.Map}.<br>
	 * The element of {@code Iterable} and the value of {@code Map} must be
	 * one of all above.<br>
	 * The key of {@code Map} must be {@link java.lang.String}.
	 * @param keys available key set.
	 * @param count space count, used for beautify.
	 * @return
	 */
	public static String stringify(Object value, Collection<String> keys, int count) {
		Processor proc = keys == null ? null : new CollectionProcesser(keys);
		
		return stringify(value, proc, count > 0 ? getSpace(count) : "");
	}
	
	/**
	 * Get the spaces.
	 * @param space
	 * @return
	 */
	private static String getSpace(int space) {
		char[] cs = new char[Math.max(0, Math.min(space, 10))];
		
		Arrays.fill(cs, ' ');
		
		return new String(cs);
	}
	
	/**
	 * Transform the java object to JSON string.
	 * @param value supported {@link java.lang.Number},
	 * {@link java.lang.String}, {@link java.lang.Boolean},
	 * {@link java.lang.Iterable}, {@link java.util.Map}.<br>
	 * The element of {@code Iterable} and the value of {@code Map} must be
	 * one of all above.<br>
	 * The key of {@code Map} must be {@link java.lang.String}.
	 * @param keys available key set.
	 * @param space space string, used for beautify.
	 * @return
	 */
	public static String stringify(Object value, Collection<String> keys, String space) {
		Processor proc = keys == null ? null : new CollectionProcesser(keys);
		
		return stringify(value, proc, space);
	}
	
	/**
	 * Transform the java object to JSON string.
	 * @param value supported {@link java.lang.Number},
	 * {@link java.lang.String}, {@link java.lang.Boolean},
	 * {@link java.lang.Iterable}, {@link java.util.Map}.<br>
	 * The element of {@code Iterable} and the value of {@code Map} must be
	 * one of all above.<br>
	 * The key of {@code Map} must be {@link java.lang.String}.
	 * @param proc process the object.
	 * @return
	 */
	public static String stringify(Object value, Processor proc) {
		return stringify(value, proc, "");
	}
	
	/**
	 * Transform the java object to JSON string.
	 * @param value supported {@link java.lang.Number},
	 * {@link java.lang.String}, {@link java.lang.Boolean},
	 * {@link java.lang.Iterable}, {@link java.util.Map}.<br>
	 * The element of {@code Iterable} and the value of {@code Map} must be
	 * one of all above.<br>
	 * The key of {@code Map} must be {@link java.lang.String}.
	 * @param proc processor to process the object.
	 * @param count space count, used for beautify.
	 * @return
	 */
	public static String stringify(Object value, Processor proc, int count) {
		return stringify(value, proc, count > 0 ? getSpace(count) : "");
	}
	
	/**
	 * Transform the java object to JSON string.
	 * @param value supported {@link java.lang.Number},
	 * {@link java.lang.String}, {@link java.lang.Boolean},
	 * {@link java.lang.Iterable}, {@link java.util.Map}.<br>
	 * The element of {@code Iterable} and the value of {@code Map} must be
	 * one of all above.<br>
	 * The key of {@code Map} must be {@link java.lang.String}.
	 * @param proc processor to process the object.
	 * @param space space string, used for beautify.
	 * @return
	 */
	public static String stringify(Object value, Processor proc, String space) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("", value);
		value = back(proc, map, "", new ArrayList<>());
		if(value == undefined) return null;
		
		return space.isEmpty() ? stringify(value, false)
			: beautify(value, substr(space, 10), 0, false);
	}
	
	/**
	 * Back walk and replace the properties.
	 * @param obj
	 * @param proc
	 * @return
	 */
	private static Object back(Processor proc, Object obj, Object key, List<Reference> refers) {
		Object value = getValue(obj, key);
		if(value instanceof JSONable) {
			value = ((JSONable)value).toJSON();
		}
		if(proc != null) {
			value = proc.process(obj, String.valueOf(key), value);
		}
		if(value == undefined) return undefined;
		
		refers.add(new Reference(value, key));
		checkCircular(value, refers);
		
		if(value instanceof List) {
			value = backArray(proc, (List<?>)value, refers);
		} else if(value instanceof Map) {
			value = backMap(proc, (Map<?, ?>)value, refers);
		}
		refers.remove(refers.size() - 1);
		
		return value;
	}
	
	/**
	 * Back walk the array.
	 * @param proc
	 * @param list
	 * @param refers 
	 * @return
	 */
	private static List<Object> backArray(Processor proc, List<?> list, List<Reference> refers) {
		List<Object> array = new ArrayList<>();
		for(int i = 0, len = list.size(); i < len; i++) {
			Object value = back(proc, list, i, refers);
			array.add(value == undefined ? null : value);
		}
		return array;
	}
	
	/**
	 * Walk the map.
	 * @param proc
	 * @param src
	 * @param refers 
	 * @return
	 */
	private static Map<String, Object> backMap(Processor proc, Map<?, ?> src, List<Reference> refers) {
		Map<String, Object> map = new LinkedHashMap<>();
		Iterator<?> iter = src.keySet().iterator();
		while(iter.hasNext()) {
			Object key = iter.next();
			Object val = back(proc, src, key, refers);
			if(val != undefined) map.put(String.valueOf(key), val);
		}
		return map;
	}
	
	/**
	 * Check circular references.
	 * @param value
	 * @param refers 
	 * @throws TypeError when circular references occurred
	 * in the {@code value}.
	 */
	private static void checkCircular(Object value, List<Reference> refers) {
		int index = circularIndex(value, refers);
		if(index >= 0) {
			throw new TypeError(refers.subList(index, refers.size()));
		}
	}
	
	/**
	 * Get the circular reference index.
	 * @param value
	 * @param refers
	 * @return
	 */
	private static int circularIndex(Object value, List<Reference> refers) {
		for(int i = refers.size() - 2; i >= 0; i--) {
			if(refers.get(i).circle(value)) return i;
		}
		return -1;
	}
	
	/**
	 * Stringify the value.
	 * @param value
	 * @param otherNull
	 * @return
	 */
	private static String stringify(Object value, boolean otherNull) {
		if(value instanceof String) {
			return encodeStr((String)value);
		} else if(value instanceof Map) {
			return map2JSON((Map<?, ?>)value);
		} else if(value instanceof Iterable) {
			return iterable2JSON((Iterable<?>)value);
		} else if(value == null
			|| value instanceof Number
			|| value instanceof Boolean) {
			return String.valueOf(value);
		} else if(value instanceof Date) {
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			cal.setTime((Date)value);
			return encodeStr(toISOString(cal));
		}
		if(otherNull) return null;
		
		throw new TypeError(value.getClass().getSimpleName());
	}
	
	/**
	 * Encode the string to JSON string.
	 * @param value
	 * @return
	 */
	private static String encodeStr(String value) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("\"");
		for(int i = 0, len = value.length(); i < len; i++) {
			char c = value.charAt(i);
			if(c >= 0 && c <= 7 ||
				c == 11 ||
				c >= 14 && c <= 31 ||
				c >= 55296 && c <= 57343) {
				sb.append("\\u").append(strict(Integer.toHexString(c), 4));
			} else {
				switch(c) {
				case '\b': { sb.append("\\b"); break; }
				case '\t': { sb.append("\\t"); break; }
				case '\n': { sb.append("\\n"); break; }
				case '\f': { sb.append("\\f"); break; }
				case '\r': { sb.append("\\r"); break; }
				case '\\':
				case '"':
					sb.append('\\');
				default:
					sb.append(c);
				}
			}
		}
		return sb.append("\"").toString();
	}
	
	/**
	 * Strict the string to limit length.
	 * @param obj
	 * @param limit
	 * @return
	 */
	private static String strict(Object obj, int limit) {
		String str = String.valueOf(obj);
		
		int len = str.length();
		if(len >= limit) return str;
		
		StringBuilder sb = new StringBuilder();
		for(int i = len; i < limit; i++) {
			sb.append('0');
		}
		return sb.append(str).toString();
	}
	
	/**
	 * Hash map to JSON object string.
	 * @param oValue2
	 * @return
	 */
	private static String map2JSON(Map<?, ?> map) {
		StringJoiner sj = new StringJoiner(",", "{", "}");
		
		Iterator<?> iter = map.keySet().iterator();
		while(iter.hasNext()) {
			Object key = iter.next();
			String pair = addKeyValuePair(key, map.get(key));
			if(pair != null) sj.add(pair);
		}
		return sj.toString();
	}
	
	/**
	 * Add the key value pair.
	 * @param key
	 * @param value
	 * @return
	 */
	private static String addKeyValuePair(Object key, Object value) {
		String str_val = stringify(value, true);
		if(str_val == null) return null;
		
		return new StringBuilder()
			.append(encodeStr(String.valueOf(key)))
			.append(":").append(str_val).toString();
	}
	
	/**
	 * List to JSON string.
	 * @param oValue
	 * @return
	 */
	private static String iterable2JSON(Iterable<?> values) {
		StringJoiner sj = new StringJoiner(",", "[", "]");
		for(Object value : values) {
			sj.add(stringify(value, true));
		}
		return sj.toString();
	}
	
	/**
	 * Transform to ISO date string.
	 * @param cal
	 * @return a date string, format as<br>
	 * {@literal yyyy-MM-ddTHH:mm:ss.SSSZ}<br>
	 * {@literal ±uuuuuu-MM-ddTHH:mm:ss.SSSZ}
	 */
	private static String toISOString(Calendar cal) {
		long millis = cal.getTimeInMillis();
		if(millis > MAX_DATE || millis < -MAX_DATE) return null;
		
		StringBuilder sb = new StringBuilder();
		int digit = millis >= MIN_4_DATE && millis < MAX_4_DATE ? 4 : 6;
		
		return sb.append(digit == 4 ? "" : millis < 0 ? "-" : "+")
				 .append(strict(cal.get(Calendar.YEAR), digit)).append('-')
				 .append(strict(cal.get(Calendar.MONTH) + 1, 2)).append('-')
				 .append(strict(cal.get(Calendar.DATE), 2)).append('T')
				 .append(strict(cal.get(Calendar.HOUR_OF_DAY), 2)).append(':')
				 .append(strict(cal.get(Calendar.MINUTE), 2)).append(':')
				 .append(strict(cal.get(Calendar.SECOND), 2)).append('.')
				 .append(strict(cal.get(Calendar.MILLISECOND), 3)).append('Z')
				 .toString();
	}
	
	/**
	 * Substring the string.
	 * @param str
	 * @param len
	 * @return
	 */
	private static String substr(String str, int len) {
		if(str == null) return "";
		
		if(str.length() < len) return str;
		
		return str.substring(0, len);
	}
	
	/**
	 * Beautify stringify.
	 * @param value
	 * @param space
	 * @param otherNull 
	 * @param indent 
	 * @return
	 */
	private static String beautify(Object value,
			String space, int indent, boolean otherNull) {
		if(value instanceof String) {
			return encodeStr((String)value);
		} else if(value instanceof Map) {
			return mapBeautify((Map<?, ?>)value, space, indent);
		} else if(value instanceof Iterable) {
			return iterableBeautify((Iterable<?>)value, space, indent);
		} else if(value == null
			|| value instanceof Number
			|| value instanceof Boolean) {
			return String.valueOf(value);
		} else if(value instanceof Date) {
			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			cal.setTime((Date)value);
			return encodeStr(toISOString(cal));
		}
		if(otherNull) return null;
		
		throw new TypeError(value.getClass().getSimpleName());
	}
	
	/**
	 * Map to beautiful JSON string.
	 * @param map
	 * @param space
	 * @param indent
	 * @return
	 */
	private static String mapBeautify(Map<?, ?> map, String space, int indent) {
		if(map.isEmpty()) return "{}";
		
		String endstr = getIndentStr(space, indent);
		String idtstr = endstr + space;
		StringJoiner sj = new StringJoiner(",\n" + idtstr,
				"{\n" + idtstr, "\n" + endstr + "}");
		
		boolean append = false;
		Iterator<?> iter = map.keySet().iterator();
		while(iter.hasNext()) {
			Object key	= iter.next();
			String pair = addBeautifyKeyValuePair(key,
				map.get(key), space, indent + 1);
			if(pair != null) {
				sj.add(pair);
				append = true;
			}
		}
		return append ? sj.toString() : "{}";
	}
	
	/**
	 * Get the indent string.
	 * @param space
	 * @param indent
	 * @return
	 */
	private static String getIndentStr(String space, int indent) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < indent; i++) {
			sb.append(space);
		}
		return sb.toString();
	}
	
	/**
	 * Add the beautiful key value pair.
	 * @param key
	 * @param value
	 * @param space 
	 * @param indent 
	 * @return
	 */
	private static String addBeautifyKeyValuePair(Object key,
			Object value, String space, int indent) {
		String str_val = beautify(value, space, indent, true);
		if(str_val == null) return null;
		
		return new StringBuilder()
			.append(encodeStr(String.valueOf(key)))
			.append(": ").append(str_val).toString();
	}
	
	/**
	 * List to beautiful JSON string.
	 * @param values
	 * @param space
	 * @param indent
	 * @return
	 */
	private static String iterableBeautify(Iterable<?> values, String space, int indent) {
		String endstr = getIndentStr(space, indent);
		String idtstr = endstr + space;
		StringJoiner sj = new StringJoiner(",\n" + idtstr,
				"[\n" + idtstr, "\n" + endstr + "]");
		
		boolean append = false;
		for(Object value : values) {
			append = true;
			sj.add(beautify(value, space, indent + 1, true));
		}
		return append ? sj.toString() : "[]";
	}
}
