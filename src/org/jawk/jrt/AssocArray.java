package org.jawk.jrt;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.math.NumberUtils;

/**
 * An AWK associative array.
 * <p>
 * The implementation requires the ability to choose,
 * at runtime, whether the keys are to be maintained in
 * sorted order or not. Therefore, the implementation
 * contains a reference to a Map (either TreeMap or
 * HashMap, depending on whether to maintain keys in
 * sorted order or not) and delegates calls to it
 * accordingly.
 * </p>
 */
public class AssocArray implements Comparator<Object> {

    /**
     * The parameter to useMapType to convert
     * this associative array to a HashMap.
     */
    public static final int MT_HASH = 2;
    /**
     * The parameter to useMapType to convert
     * this associative array to a LinkedHashMap.
     */
    public static final int MT_LINKED = 2 << 1;
    /**
     * The parameter to useMapType to convert
     * this associative array to a TreeMap.
     */
    public static final int MT_TREE = 2 << 2;
    

    private Map<Object, Object> map;
	
	// RMP
	private VariableManager myVm;
	private boolean myIsHere = false;
	private int myType = MT_HASH;
	
	// RMP
    public AssocArray(Map<Object, Object> map, VariableManager vm) {
        this.map = map;
        myVm = vm;
        myType = 0;
    }
    
	public AssocArray(boolean sortedArrayKeys, VariableManager vm) {
		if (sortedArrayKeys) {
			map = new TreeMap<Object, Object>(this);
			// RMP
			myType = MT_TREE;
		} else {
			map = new HashMap<Object, Object>();
			// RMP
            myType = MT_HASH;
		}
		
		// RMP
		myVm = vm;
	}

	// RMP
	public int size() {
	    return map.size();
	}

	/**
	 * Convert the map which backs this associative array
	 * into one of HashMap, LinkedHashMap, or TreeMap.
	 *
	 * @param mapType Can be one of MT_HASH, MT_LINKED,
	 *   or MT_TREE.
	 */
	public void useMapType(int mapType) {
		assert map.isEmpty();
		switch (mapType) {
			case MT_HASH:
			    // RMP
			    if (myType == MT_HASH) {
			        if (!map.isEmpty()) {
			            map.clear();
			        }
			    } else {
			        map = new HashMap<Object, Object>();
		            myType = MT_HASH;
			    }
				break;
			case MT_LINKED:
                // RMP
                if (myType == MT_LINKED) {
                    if (!map.isEmpty()) {
                        map.clear();
                    }
                } else {
                    map = new LinkedHashMap<Object, Object>();
                    myType = MT_LINKED;
                }
				break;
			case MT_TREE:
                // RMP
                if (myType == MT_TREE) {
                    if (!map.isEmpty()) {
                        map.clear();
                    }
                } else {
                    map = new TreeMap<Object, Object>(this);
                    myType = MT_TREE;
                }
				break;
			default:
				throw new Error("Invalid map type : " + mapType);
		}
	}
	
	
	// RMP
	public int getMapType() {
	    return myType;
	}
	
	
	// RMP
	public VariableManager getVm() {
	    return myVm;
	}
	
	
	// RMP
	private String toKey(Object o) {
	    Double d = null;
	    
        String key ="";
        
	    if (o instanceof Number) {
	        if (o instanceof Double) {
	            d = (Double) o;
	        }
	    } else if (myType == MT_TREE) {
	        String s = o.toString(); //.trim();
	        if (NumberUtils.isCreatable(s)) {
	            d = JRT.toDouble(s);
	        }
	    }
	    
	    if (d != null) {
	        if (d.intValue() == d.doubleValue()) {
	            o = d.intValue();
	        }
	    }

	    if (o instanceof String) {
	        key = (String) o;
	    } else {
	        key = JRT.toAwkString(o, myVm.getCONVFMT());
	    }
	    
	    return key;
	}

	/**
	 * Provide a string representation of the delegated
	 * map object.
	 * It exists to support the _DUMP keyword.
	 */
	public String mapString() {
	    // RMP
	    if (myIsHere) {
	        throw new AwkRuntimeException("Infinite loop printing associative array.");
	    }
	    myIsHere = true;
		StringBuilder sb = new StringBuilder().append('{');
		int cnt = 0;
        for (Object o : map.keySet()) {
			if (cnt > 0) {
				sb.append(", ");
			}
		    sb.append(o.toString().replaceAll(myVm.getSUBSEP().toString(), ","));
			sb.append('=');
			Object o2 = map.get(o);
			if (o2 instanceof AssocArray) {
				sb.append(((AssocArray) o2).mapString());
			} else {
                sb.append(JRT.toAwkString(o2, myVm.getCONVFMT()));
			}
			++cnt;
		}
        myIsHere = false;
		return sb.append('}').toString();
	}

	/** a "null" value in Awk */
	private static final String BLANK = "";

	/**
	 * Test whether a particular key is
	 * contained within the associative array.
	 * Unlike get(), which adds a blank (null)
	 * reference to the associative array if the
	 * element is not found, isIn will not.
	 * It exists to support the IN keyword.
	 */
	public boolean isIn(Object key) {
        // RMP Fixed a bug.
	    return map.get(toKey(key)) != null;
	}

	/**
	 * Get the value of an associative array
	 * element given a particular key.
	 * If the key does not exist, a null value
	 * (blank string) is inserted into the array
	 * with this key, and the null value is returned.
	 */
	public Object get(Object key) {
        // RMP Fixed a bug.
        key = toKey(key);
        
        Object result = map.get(key);
		if (result == null) {
			// based on the AWK specification:
			// Any reference (except for IN expressions) to a non-existent
			// array element will automatically create it.
			result = BLANK;
			map.put(key, result);
		}
		return result;
	}

	public Object put(Object key, Object value) {
	    // RMP Fixed a bug
	    return map.put(toKey(key), value);
	}

	/**
	 * Added to support insertion of primitive key types.
	 */
	public Object put(int key, Object value) {
        // RMP Fixed a bug.
        return map.put(String.valueOf(key), value);
	}

	public Set<Object> keySet() {
		return map.keySet();
	}

	public void clear() {
		map.clear();
	}
	
	public Map<Object, Object> getMap() {
	    return Collections.unmodifiableMap(map);
	}

	public Object remove(Object key) {
	    // RMP Fixed a bug
        return map.remove(toKey(key));
	}

	@Override
	public String toString() {
	    // RMP
		throw new AwkRuntimeException("Cannot convert an associative array to a string.");
	}

	/**
	 * Comparator implementation used by the TreeMap
	 * when keys are to be maintained in sorted order.
	 */
	@Override
	public int compare(Object o1, Object o2) {
	    
	    // RMP Fixed a bug.
	    
//        String s1 = o1.toString();
//        String s2 = o2.toString();
        String s1 = (String)o1;
        String s2 = (String)o2;

        if (NumberUtils.isCreatable(s1)) { //.trim()
            Number n = JRT.toDouble(s1);
            if (n.doubleValue() == n.intValue()) {
                n = n.intValue();
            }
            o1 = n;
        }
        
        if (NumberUtils.isCreatable(s2)) { //.trim()
            Number n = JRT.toDouble(s2);
            if (n.doubleValue() == n.intValue()) {
                n = n.intValue();
            }
            o2 = n;
        }
        
        if (o1 instanceof String || o2 instanceof String) {
            // Use string comparison
            return s1.compareTo(s2);
        } else if (o1 instanceof Double || o2 instanceof Double) {
            Double d1 = ((Number)o1).doubleValue();
            Double d2 = ((Number)o2).doubleValue();
            return d1.compareTo(d2);
        } else {
            Integer i1 = (Integer)o1;
            Integer i2 = (Integer)o2;
            return i1.compareTo(i2);
        }
	}

	public String getMapVersion() {
		return map.getClass().getPackage().getSpecificationVersion();
	}
	
	// RMP
	public boolean equals(Object o) {
	    if (!(o instanceof AssocArray)) {
	        return false;
	    }
	    
	    return ((AssocArray)o).map.equals(map);
	}
}
