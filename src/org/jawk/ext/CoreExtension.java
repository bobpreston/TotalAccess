
package org.jawk.ext;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jawk.NotImplementedError;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.AwkRuntimeException;
import org.jawk.jrt.BlockObject;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagacy.totalAccess.jrt.AwkNode;

/**
 * Extensions which make developing in Jawk and
 * interfacing other extensions with Jawk
 * much easier.
 * <p>
 * The extension functions which are available are as follows:
 * <ul>
 * <li><strong>Array</strong> - <code><font size=+1>Array(array,1,3,5,7,9)</font></code><br>
 * Inserts elements into an associative array whose keys
 * are ordered non-negative integers, and the values
 * are the arguments themselves. The first argument is
 * the associative array itself.
 * <li><strong>Map/HashMap/TreeMap/LinkedMap</strong> - <code><font size=+1>Map(map,k1,v1,k2,v2,...,kN,vN)</font></code>,
 * or <code><font size=+1>Map(k1,v1,k2,v2,...,kN,vN)</font></code>.<br>
 * Build an associative array with its keys/values as
 * parameters. The odd parameter count version takes
 * the map name as the first parameter, while the even
 * parameter count version returns an anonymous associative
 * array for the purposes of providing a map by function
 * call parameter.<br>
 * Map/HashMap configures the associative array as a
 * hash map, TreeMap as an ordered map, and LinkedMap
 * as a map which traverses the key set in order of
 * insertion.
 * <li><strong>MapUnion</strong> - <code><font size=+1>MapUnion(map,k1,v1,k2,v2,...,kN,vN)</font></code><br>
 * Similar to Map, except that map is not cleared prior
 * to populating it with key/value pairs from the
 * parameter list.
 * <li><strong>MapCopy</strong> - <code><font size=+1>cnt = MapCopy(aaTarget, aaSource)</font></code><br>
 * Clears the target associative array and copies the
 * contents of the source associative array to the
 * target associative array.
 * <li><strong>TypeOf</strong> - <code><font size=+1>typestring = TypeOf(item)</font></code><br>
 * Returns one of the following depending on the argument:
 * 	<ul>
 * 	<li>"String"
 * 	<li>"Integer"
 * 	<li>"AssocArray"
 * 	<li>"Reference" (see below)
 * 	</ul>
 * <li><strong>String</strong> - <code><font size=+1>str = String(3)</font></code><br>
 * Converts its argument to a String.
 * Similar to the _STRING extension, but provided
 * for completeness/normalization.
 * <li><strong>Double</strong> - <code><font size=+1>dbl = Double(3)</font></code><br>
 * Converts its argument to a Double.
 * Similar to the _DOUBLE extension, but provided
 * for completeness/normalization.
 * <li><strong>Halt</strong> - <code><font size=+1>Halt()</font></code><br>
 * Similar to exit(), except that END blocks are
 * not executed if Halt() called before END
 * block processing.
 * <li><strong>Timeout</strong> - <code><font size=+1>r = Timeout(300)</font></code><br>
 * A blocking function which waits N milliseconds
 * before unblocking (continuing). This is useful in scripts which
 * employ blocking, but occasionally needs to break out
 * of the block to perform some calculation, polling, etc.
 * <li><strong>Throw</strong> - <code><font size=+1>Throw("this is an awkruntimeexception")</font></code><br>
 * Throws an AwkRuntimeException from within the script.
 * <li><strong>Version</strong> - <code><font size=+1>print Version(aa)</font></code><br>
 * Prints the version of the Java class which represents the parameter.
 * <li><strong>Date</strong> - <code><font size=+1>str = Date()</font></code><br>
 * Similar to the Java equivalent : str = new Date().toString();
 * <li><strong>FileExists</strong> - <code><font size=+1>b = FileExists("/a/b/c")</font></code><br>
 * Returns 0 if the file doesn't exist, 1 otherwise.
 * <li><strong>NewRef[erence]/Dereference/DeRef/Unreference/UnRef/etc.</strong> -
 * Reference Management Functions.</font></code><br>
 * These are described in detail below.
 * </ul>
 * </p>
 * <p>
 * <h1>Reference Management</h1>
 * AWK's memory model provides only 4 types of variables
 * for use within AWK scripts:
 * <ul>
 * <li>Integer
 * <li>Double
 * <li>String
 * <li>Associative Array
 * </ul>
 * Variables can hold any of these types. However, unlike
 * for scalar types (integer/double/string), AWK applies
 * the following restrictions with regard to associative
 * arrays:
 * <ul>
 * <li>Associative array assignments (i.e., assocarray1 = associarray2)
 *	are prohibited.
 * <li>Functions cannot return associative arrays.
 * </ul>
 * These restrictions, while sufficient for AWK, are detrimental
 * to extensions because associative arrays are excellent vehicles
 * for configuration and return values for user extensions.
 * Plus, associative arrays can be overriden, which can be used
 * to enforce type safety within user extensions. Unfortunately, the
 * memory model restrictions make using associative arrays in this
 * capacity very difficult.
 * </p>
 * <p>
 * We attempt to alleviate these difficulties by adding references
 * to Jawk via the CoreExtension module.
 * References convert associative arrays into
 * unique strings called <strong>reference handles</strong>.
 * Since reference handles are strings, they can be
 * assigned and returned via AWK functions without restriction.
 * And, reference handles are then used by other reference extension
 * functions to perform common associative array operations, such as
 * associative array cell lookup and assignment, key existence
 * check, and key iteration.
 * </p>
 * <p>
 * The reference model functions are explained below:
 * <ul>
 * <li><strong>NewRef / NewReference</strong> - <code><font size=+1>handle = NewRef(assocarray)</font></code><br>
 * Store map into reference cache. Return the unique string handle
 * for this associative array.
 * <li><strong>DeRef / Dereference</strong> - <code><font size=+1>val = DeRef(handle, key)</font></code><br>
 * Return the cell value of the associative array referenced by the key.
 * In other words:
 * <blockquote><pre>
 * return assocarray[key]</pre></blockquote>
 * <li><strong>UnRef / Unreference</strong> - <code><font size=+1>UnRef(handle)</font></code><br>
 * Eliminate the reference occupied by the reference cache.
 * <li><strong>InRef</strong> - <code><font size=+1>while(key = InRef(handle)) ...</font></code><br>
 * Iterate through the key-set of the associative array
 * referred to by handle in the reference cache.
 * This is similar to:
 * <blockquote><pre>
 * for (key in assocarray)
 * 	...</pre></blockquote>
 * where <code>assocarray</code> is the associative array referred to by
 * handle in the reference cache.
 * <br>
 * <strong>Warning:</strong> unlike the IN keyword, InRef
 * will maintain state regardless of scope. That is,
 * if one were to break; out of the while loop above,
 * the next call to InRef() will be the next anticipated
 * element of the <code>assoc</code> array.
 * <li><strong>IsInRef</strong> - <code><font size=+1>b = IsInRef(handle, key)</font></code><br>
 * Checks whether the associative array in the reference cache
 * contains the key. This is similar to:
 * <blockquote><pre>
 * if (key in assocarray)
 *	...</pre></blockquote>
 * where <code>assocarray</code> is the associative array referred to by
 * handle in the reference cache.
 * <li><strong>DumpRefs</strong> - <code><font size=+1>DumpRefs()</font></code><br>
 * Dumps the reference cache to stdout.
 * </ul>
 * </p>
 */
public class CoreExtension extends AbstractExtension implements JawkExtension {

    // RMP
    private CoreExtension instance = this;
	//private static CoreExtension instance = null; // FIXME Ugly form of singleton implementation (which is ugly by itsself)
    //private static final Object INSTANCE_LOCK = new Object();
	private static final Logger LOG = LoggerFactory.getLogger(CoreExtension.class);

	private int refMapIdx = 0;
	private Map<String, Object> referenceMap = new HashMap<String, Object>();
	private Map<AssocArray, Iterator> iterators = new HashMap<AssocArray, Iterator>();
	private static final Integer ZERO = Integer.valueOf(0);
	private static final Integer ONE = Integer.valueOf(1);
	private int waitInt = 0;

	// single threaded, so one Date object (unsynchronized) will do
	private final Date dateObj = new Date();
	private final SimpleDateFormat dateFormat = new SimpleDateFormat();

	private final BlockObject timeoutBlocker = new BlockObject() {

		@Override
		public String getNotifierTag() {
			return "Timeout";
		}

		@Override
		public final void block()
				throws InterruptedException
		{
			synchronized (timeoutBlocker) {
				timeoutBlocker.wait(waitInt);
			}
		}
	};

	public CoreExtension() {
	    // RMP
//		synchronized (INSTANCE_LOCK) {
//			if (instance == null) {
//				instance = this;
//			} else {
//			    // RMP
//				//LOG.warn("Multiple CoreExtension instances in this VM. Using original instance.");
//			}
//		}
	}

	@Override
	public String getExtensionName() {
		return "Core Extension";
	}

	@Override
	public String[] extensionKeywords() {
		return new String[] {
		        // RMP
				"createSortedArray",	// i.e. Array(array,1,3,5,7,9,11)
                "createListArray",  // i.e. Array(array,1,3,5,7,9,11)
                "clearArray",  // i.e. Array(array,1,3,5,7,9,11)
                "mergeArray",  // i.e. Array(array,1,3,5,7,9,11)
                "copyArray",  // i.e. cnt = MapCopy(aaTarget, aaSource)
                "cloneArray",
                "arrayArg",
                "arrayLength",
                "subArray",
                "ref",
                
				"Map",		// i.e. Map(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"HashMap",	// i.e. HashMap(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"TreeMap",	// i.e. TreeMap(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"LinkedMap",	// i.e. LinkedMap(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				"MapUnion",	// i.e. MapUnion(assocarray, "hi", "there", "testing", 3, 5, Map("item1", "item2", "i3", 4))
				
                // RMP
				"typeof",	// i.e. typestring = TypeOf(item)
				"string",	// i.e. str = String(3)
				"double",	// i.e. dbl = Double(3)
				"integer",
				"isNumeric",
				
				"Halt",		// i.e. Halt()
				"Dereference",	// i.e. f(Dereference(r1))
				"DeRef",	// i.e. 	(see above, but replace Dereference with DeRef)
				"NewReference",	// i.e. ref = NewReference(Map("hi","there"))
				"NewRef",	// i.e. 	(see above, but replace Reference with Ref)
				"Unreference",	// i.e. b = Unreference(ref)
				"UnRef",	// i.e. 	(see above, but replace Unreference with UnRef)
				"InRef",	// i.e. while(k = InRef(r2)) [ same as for(k in assocarr) ]
				"IsInRef",	// i.e. if (IsInRef(r1, "key")) [ same as if("key" in assocarr) ]
				"DumpRefs",	// i.e. DumpRefs()
				"Timeout",	// i.e. r = Timeout(300)
				"Throw",	// i.e. Throw("this is an awkruntimeexception")
				"Version",	// i.e. print Version(aa)

				// RMP
				"date",		// i.e. str = Date()
				"convertDate",
				"fileExists",	// i.e. b = FileExists("/a/b/c")
				"deleteFile",
				"renameFile",
				"mkdir",
				"unescapeString",
                "escapeString",
                "trim"
				};
	}

	@Override
	public int[] getAssocArrayParameterPositions(String extensionKeyword, int numArgs) {
		if ((      extensionKeyword.equals("Map")
				|| extensionKeyword.equals("HashMap")
				|| extensionKeyword.equals("LinkedMap")
				|| extensionKeyword.equals("TreeMap")) && ((numArgs % 2) == 1))
		{
			// first argument of a *Map() function
			// must be an associative array
			return new int[] {0};
		} else if (extensionKeyword.equals("Array")) {
			// first argument of Array must be
			// an associative array
			return new int[] {0};
		} else if (extensionKeyword.equals("NewReference")
				|| extensionKeyword.equals("NewRef"))
		{
			if (numArgs == 1) {
				return new int[] {0};
			} else {
				return super.getAssocArrayParameterPositions(extensionKeyword, numArgs);
			}
		} else {
			return super.getAssocArrayParameterPositions(extensionKeyword, numArgs);
		}
	}

	@Override
	public Object invoke(String keyword, Object[] args) {
		if        (keyword.equals("Map") || keyword.equals("HashMap")) {
			return map(args, getVm(), AssocArray.MT_HASH);
		} else if (keyword.equals("LinkedMap")) {
			return map(args, getVm(), AssocArray.MT_LINKED);
		} else if (keyword.equals("TreeMap")) {
			return map(args, getVm(), AssocArray.MT_TREE);
		} else if (keyword.equals("MapUnion")) {
			return mapUnion(args, getVm(), AssocArray.MT_LINKED);
			
        // RMP
		} else if (keyword.equals("copyArray")) {
            checkNumArgs(args, 2, keyword);
            if (!(args[0] instanceof AssocArray) || !(args[1] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword + "() requires an array");
            }
			return copyArray(args);
        } else if (keyword.equals("cloneArray")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword + "() requires an array");
            }
            return cloneArray(args);
        } else if (keyword.equals("arrayArg")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword + "() requires an array");
            }
            return args[0];
		} else if (keyword.equals("createSortedArray")) {
			return createSortedArray(args, getVm());
        } else if (keyword.equals("createListArray")) {
            return createListArray(args, getVm());
        } else if (keyword.equals("clearArray")) {
            checkNumArgs(args, 1, keyword);
            return clearArray(args, getVm());
        } else if (keyword.equals("mergeArray")) {
            return mergeArray(args);
        } else if (keyword.equals("arrayLength")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword + "() requires an array");
            }
            return ((AssocArray)args[0]).size();
        } else if (keyword.equals("subArray")) {
            checkNumArgs(args, 3, keyword);
            if (!(args[0] instanceof AssocArray) || !(args[2] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword + "() requires an array");
            }
            return subArray(args);
		} else if (keyword.equals("typeof")) {
			checkNumArgs(args, 1, keyword);
			return typeOf(args[0], getVm());
		} else if (keyword.equals("string")) {
			checkNumArgs(args, 1, keyword);
			return toString(args[0], getVm());
		} else if (keyword.equals("double")) {
			checkNumArgs(args, 1, keyword);
			return toDouble(args[0], getVm());
        } else if (keyword.equals("integer")) {
            checkNumArgs(args, 1, keyword);
            return toInteger(args[0], getVm());
            
        } else if (keyword.equals("isNumeric")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException("Argument is not a string in isNumeric()");
            }
            
            return NumberUtils.isCreatable(args[0].toString().trim()) ? ONE : ZERO;
            
		} else if (keyword.equals("Halt")) {
			if (args.length == 0) {
				Runtime.getRuntime().halt(0);
			} else if (args.length == 1) {
				Runtime.getRuntime().halt((int) JRT.toDouble(args[0]));
			} else {
				throw new IllegalAwkArgumentException(keyword + " requires 0 or 1 argument, not " + args.length);
			}
		} else if (keyword.equals("NewReference") || keyword.equals("NewRef")) {
			if (args.length == 1) {
				return newReference(args[0]);
			} else if (args.length == 3) {
				return newReference(toAwkString(args[0]), args[1], args[2]);
			} else {
				throw new IllegalAwkArgumentException(keyword + " requires 1 or 3 arguments, not " + args.length);
			}
		} else if (keyword.equals("Dereference") || keyword.equals("DeRef")) {
			if (args.length == 1) {
				return resolve(dereference(args[0], getVm()), getVm());
			} else if (args.length == 2) {
				return resolve(dereference(toAwkString(args[0]), args[1], getVm()), getVm());
			} else {
				throw new IllegalAwkArgumentException(keyword + " requires 1 or 2 arguments, not " + args.length);
			}
		} else if (keyword.equals("Unreference") || keyword.equals("UnRef")) {
			checkNumArgs(args, 1, keyword);
			return unreference(args[0], getVm());
		} else if (keyword.equals("InRef")) {
			checkNumArgs(args, 1, keyword);
			return inref(args[0], getVm());
		} else if (keyword.equals("IsInRef")) {
			checkNumArgs(args, 2, keyword);
			return isInRef(args[0], args[1], getVm());
		} else if (keyword.equals("DumpRefs")) {
			checkNumArgs(args, 0, keyword);
			dumpRefs();
		} else if (keyword.equals("Timeout")) {
			checkNumArgs(args, 1, keyword);
			return timeout((int) JRT.toDouble(args[0]));
		} else if (keyword.equals("Throw")) {
			throw new AwkRuntimeException(Arrays.toString(args));
		} else if (keyword.equals("Version")) {
			checkNumArgs(args, 1, keyword);
			return version(args[0]);
			
		// RMP
		} else if (keyword.equals("date")) {
			if (args.length == 0) {
				return date();
			} else if (args.length == 1) {
				return date(toAwkString(args[0]));
			} else {
				throw new IllegalAwkArgumentException(keyword + "() expects 0 or 1 argument, not " + args.length);
			}
        } else if (keyword.equals("convertDate")) {
            return convertDate(args);
		} else if (keyword.equals("fileExists")) {
			checkNumArgs(args, 1, keyword);
			if (new File(toAwkString(args[0])).exists()) {
		        return ONE;
		    } else {
		        return ZERO;
		    }
        } else if (keyword.equals("deleteFile")) {
            checkNumArgs(args, 1, keyword);
            if (new File(toAwkString(args[0])).delete()) {
                return ONE;
            } else {
                return ZERO;
            }
        } else if (keyword.equals("renameFile")) {
            checkNumArgs(args, 2, keyword);
            if (new File(toAwkString(args[0])).renameTo(new File(toAwkString(args[1])))) {
                return ONE;
            } else {
                return ZERO;
            }
        } else if (keyword.equals("mkdir")) {
            checkNumArgs(args, 1, keyword);
            if (new File(toAwkString(args[0])).mkdir()) {
                return ONE;
            } else {
                return ZERO;
            }
        } else if (keyword.equals("unescapeString")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException("Argument is not a string in unescapeString()");
            }
            
            return StringEscapeUtils.unescapeJava(args[0].toString());
        } else if (keyword.equals("escapeString")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException("Argument is not a string in escapeString()");
            }
            return StringEscapeUtils.escapeJava(args[0].toString());
        } else if (keyword.equals("trim")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException("Argument is not a string in trim()");
            }
            return args[0].toString().trim();
		} else {
			throw new NotImplementedError(keyword);
		}
		// never reached
		return null;
	}

	private Object resolve(Object arg, VariableManager vm) {

		Object obj = arg;
		while (true) {
			if (obj instanceof AssocArray) {
				return obj;
			}
			String argCheck = toAwkString(obj);
			if (referenceMap.get(argCheck) != null) {
				obj = referenceMap.get(argCheck);
			} else {
				return obj;
			}
		}
	}

	// RMP
	String newReference(Object arg) {
		if (!(arg instanceof AssocArray)) { // FIXME see other FIXME below
			throw new IllegalAwkArgumentException("NewRef[erence] requires an assoc array, not " + arg.getClass().getName());
		}

		// otherwise, set the reference and return the new key

		// get next refmapIdx
		int refIdx = instance.refMapIdx++;
		// inspect the argument
		String argStr;
		if (arg instanceof AssocArray) { // FIXME This does not make sense with the FIXME marked line above
			argStr = arg.getClass().getName();
		} else {
			argStr = arg.toString();
		}
		if (argStr.length() > 63) {
			argStr = argStr.substring(0, 60) + "...";
		}
		// build Reference (scalar) string to this argument
		String retval = "@REFERENCE@ " + refIdx + " <" + argStr + ">";
		instance.referenceMap.put(retval, arg);
		return retval;
	}

	// this version assigns an assoc array a key/value pair
	// RMP
	Object newReference(String refstring, Object key, Object value) {
		AssocArray aa = (AssocArray) instance.referenceMap.get(refstring);
		if (aa == null) {
			throw new IllegalAwkArgumentException("AssocArray reference doesn't exist.");
		}
		return aa.put(key, value);
	}

	// this version assigns an object to a reference
	private Object dereference(Object arg, VariableManager vm) {
		// return the reference if the arg is a reference key
		if (arg instanceof AssocArray) {
			throw new IllegalAwkArgumentException("an assoc array cannot be a reference handle");
		} else {
			String argCheck = toAwkString(arg);
			return dereference(argCheck);
		}
	}

	// split this out for static access by other extensions
	// RMP
	Object dereference(String argCheck) {
		if (instance.referenceMap.get(argCheck) != null) {
			return instance.referenceMap.get(argCheck);
		} else {
			throw new IllegalAwkArgumentException(argCheck + " not a valid reference");
		}
	}

	// this version assumes an assoc array is stored as a reference,
	// and to retrieve the stored value
	// RMP
	Object dereference(String refstring, Object key, VariableManager vm) {
		AssocArray aa = (AssocArray) instance.referenceMap.get(refstring);
		if (aa == null) {
			throw new IllegalAwkArgumentException("AssocArray reference doesn't exist.");
		}
		if (!(key instanceof AssocArray)) {
			// check if key is a reference string!
			String keyCheck = instance.toAwkString(key);
			if (instance.referenceMap.get(keyCheck) != null) {
				// assume it is a reference rather than an assoc array key itself
				key = instance.referenceMap.get(keyCheck);
			}
		}
		return aa.get(key);
	}

	// RMP
	int unreference(Object arg, VariableManager vm) {
		String argCheck = instance.toAwkString(arg);
		if (instance.referenceMap.get(argCheck) == null) {
			throw new IllegalAwkArgumentException("Not a reference : " + argCheck);
		}

		instance.referenceMap.remove(argCheck);
		assert instance.referenceMap.get(argCheck) == null;
		return 1;
	}

	private Object inref(Object arg, VariableManager vm) {
		if (arg instanceof AssocArray) {
			throw new IllegalAwkArgumentException("InRef requires a Reference (string) argument, not an assoc array");
		}
		String argCheck = toAwkString(arg);
		if (referenceMap.get(argCheck) == null) {
			throw new IllegalAwkArgumentException("Not a reference : " + argCheck);
		}
		Object o = referenceMap.get(argCheck);
		if (!(o instanceof AssocArray)) {
			throw new IllegalAwkArgumentException("Reference not an assoc array. ref.class = " + o.getClass().getName());
		}

		AssocArray aa = (AssocArray) o;

		// use an inMap to keep track of existing iterators

		//Iterator<Object> iter = iterators.get(aa);
		Iterator iter = iterators.get(aa);
		if (iter == null) //iterators.put(aa, iter = aa.keySet().iterator());
		// without a new Collection, modification to the
		// assoc array during iteration causes a ConcurrentModificationException
		{
			iter = new ArrayList<Object>(aa.keySet()).iterator();
			iterators.put(aa, iter);
		}

		Object retval = null;

		while (iter.hasNext()) {
			retval = iter.next();
			if (retval instanceof String && retval.toString().equals("")) {
				throw new AwkRuntimeException("Assoc array key contains a blank string ?!");
			}
			break;
		}

		if (retval == null) {
			iterators.remove(aa);
			retval = "";
		}

		if (retval instanceof AssocArray) {
			// search if item is referred to already
			for (String ref : referenceMap.keySet()) {
				if (referenceMap.get(ref) == retval) {
					return ref;
				}
			}
			// otherwise, return new reference to this item
			//return newReference(argCheck, retval);
			return newReference(retval);
		} else {
			return retval;
		}
	}

	private int isInRef(Object ref, Object key, VariableManager vm) {
		if (ref instanceof AssocArray) {
			throw new IllegalAwkArgumentException("Expecting a reference string for the 1st argument, not an assoc array.");
		}
		String refstring = toAwkString(ref);
		return isInRef(refstring, key);
	}

	// RMP
	int isInRef(String refstring, Object key) {
		Object o = instance.referenceMap.get(refstring);
		if (o == null) {
			throw new IllegalAwkArgumentException("Invalid refstring : " + refstring);
		}
		AssocArray aa = (AssocArray) o;
		return aa.isIn(key) ? ONE : ZERO;
	}

	private void dumpRefs() {
		for (Map.Entry<String, Object> entry : referenceMap.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof AssocArray) {
				value = ((AssocArray) value).mapString();
			}
			LOG.info("REF : {} = {}", new Object[] {entry.getKey(), value});
		}
	}

	// RMP
	String typeOf(Object arg, VariableManager vm) {
		if (arg instanceof AssocArray) {
			return "AssocArray";
		} else if (arg instanceof Integer) {
			return "Integer";
		} else if (arg instanceof Double) {
			return "Double";
        } else if (arg instanceof AwkNode) {
            return "Node";
		} else {
			String stringRep = instance.toAwkString(arg);
			if (instance.referenceMap.get(stringRep) != null) {
				return "Reference";
			} else {
				return "String";
			}
		}
	}

	private int get(AssocArray retval, AssocArray map, Object key) {
		retval.clear();
		retval.put(0, map.get(key));
		return 1;
	}

	private Object toScalar(AssocArray aa) {
		return aa.get(0);
	}

	private Object map(Object[] args, VariableManager vm, int mapType) {
		if (args.length % 2 == 0) {
			return subMap(args, vm, mapType);
		} else {
			return topLevelMap(args, vm, mapType, false);	// false = map assignment
		}
	}

	private Object mapUnion(Object[] args, VariableManager vm, int mapType) {
		return topLevelMap(args, vm, mapType, true);	// true = map union
	}

	private int topLevelMap(Object[] args, VariableManager vm, int mapType, boolean mapUnion) {
		AssocArray aa = (AssocArray) args[0];
		if (!mapUnion) {
			aa.clear();
			aa.useMapType(mapType);
		}
		int cnt = 0;
		for (int i = 1; i < args.length; i += 2) {
			if (args[i] instanceof AssocArray) {
				args[i] = newReference(args[i]);
			}
			if (args[i + 1] instanceof AssocArray) {
				args[i + 1] = newReference(args[i + 1]);
			}

			aa.put(args[i], args[i + 1]);

			++cnt;
		}
		return cnt;
	}

	private AssocArray subMap(Object[] args, VariableManager vm, int mapType) {
		AssocArray aa = new AssocArray(false, vm);
		//aa.clear();
		aa.useMapType(mapType);
		for (int i = 0; i < args.length; i += 2) {
			if (args[i] instanceof AssocArray) {
				args[i] = newReference(args[i]);
			}
			if (args[i + 1] instanceof AssocArray) {
				args[i + 1] = newReference(args[i + 1]);
			}

			aa.put(args[i], args[i + 1]);
		}
		return aa;
	}

	// RMP
	
    private Object clearArray(Object[] args, VariableManager vm) {
//        if (args.length == 0) {
//            throw new IllegalAwkArgumentException("No argument for clearArray()");
//        }
        if (!(args[0] instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("Argument is not an array for clearArray()");
        }
        AssocArray aa = (AssocArray) args[0];
        aa.clear();
        return aa;
    }
    
    private Object mergeArray(Object[] args) {
        if (args.length == 0) {
            throw new IllegalAwkArgumentException("No arguments for mergeArray()");
        }
        if (!(args[0] instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("1st argument is not an array for mergeArray()");
        }
        AssocArray aaTarget = (AssocArray) args[0];
        
        for (int i = 1; i < args.length; i++) {
            if (!(args[i] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Scalar argument in mergeArray()");
            }
            AssocArray aaSource = (AssocArray) args[i];
            
            for (Object o : aaSource.keySet()) {
                aaTarget.put(o, aaSource.get(o));
            }
        }
        return aaTarget;
    }
    
	private Object createSortedArray(Object[] args, VariableManager vm) {
        if (args.length < 1) {
            throw new IllegalAwkArgumentException("Invalid number of arguments for createSortedArray()");
        }
        if (!(args[0] instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("1st argument is not an array for createSortedArray()");
        }
		AssocArray aa = (AssocArray) args[0];
		//aa.clear();
		aa.useMapType(AssocArray.MT_TREE);
		String subsep = toAwkString(vm.getSUBSEP());
        Object dimension = 0;
		if (args.length > 1) {
            dimension = toInteger(args[1], vm);
		}
        if (!(dimension instanceof Integer)) {
            throw new IllegalAwkArgumentException("2nd argument is not an integer for createSortedArray()");
        }
        int dim = (Integer)dimension;
        if (dim < 0) {
            throw new IllegalAwkArgumentException("Negative 2nd argument for createSortedArray()");
        }
        if ((dim != 0) && ((args.length - 2) % (dim + 1)) != 0) {
            throw new IllegalAwkArgumentException("Invalid number of arguments for createSortedArray()");
        }
        
        List<String> indexList = new ArrayList<String>();
		int cnt = 1;
		for (int i = 2; i < args.length; ++i) {
			Object o = args[i];
			if (o instanceof AssocArray) {
	            throw new IllegalAwkArgumentException("Array argument in createSortedArray()");
			} else if (indexList.size() < dim) {
			    indexList.add(o.toString());
			} else {
			    if (indexList.size() != 0) {
	                String idx = "";
			        boolean isFirst = true;
			        for (String index : indexList) {
			            if (!isFirst) {
			                idx += subsep;
			            }
			            isFirst = false;
			            idx += index;
			        }
			        indexList.clear();
	                aa.put(idx, o);
			    } else {
	                aa.put(cnt, o);
			    }
			}
			
			cnt++;
		}
		return aa;
	}

    private Object createListArray(Object[] args, VariableManager vm) {
        if (args.length < 1) {
            throw new IllegalAwkArgumentException("Invalid number of arguments for createListArray()");
        }
        if (!(args[0] instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("1st argument is not an array for createListArray()");
        }
        AssocArray aa = (AssocArray) args[0];
        //aa.clear();
        aa.useMapType(AssocArray.MT_LINKED);
        String subsep = toAwkString(vm.getSUBSEP());
        Object dimension = 0;
        if (args.length > 1) {
            dimension = toInteger(args[1], vm);
        }
        if (!(dimension instanceof Integer)) {
            throw new IllegalAwkArgumentException("2nd argument is not an integer for createListArray()");
        }
        int dim = (Integer)dimension;
        if (dim < 0) {
            throw new IllegalAwkArgumentException("Negative 2nd argument for createListArray()");
        }
        if ((dim != 0) && ((args.length - 2) % (dim + 1)) != 0) {
            throw new IllegalAwkArgumentException("Invalid number of arguments for createListArray()");
        }
        
        List<String> indexList = new ArrayList<String>();
        int cnt = 1;
        for (int i = 2; i < args.length; ++i) {
            Object o = args[i];
            if (o instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Array argument in createListArray()");
            } else if (indexList.size() < dim) {
                indexList.add(o.toString());
            } else {
                if (indexList.size() != 0) {
                    String idx = "";
                    boolean isFirst = true;
                    for (String index : indexList) {
                        if (!isFirst) {
                            idx += subsep;
                        }
                        isFirst = false;
                        idx += index;
                    }
                    indexList.clear();
                    aa.put(idx, o);
                } else {
                    aa.put(cnt, o);
                }
            }
            
            cnt++;
        }
        return aa;
    }
    
	/*private AssocArray subarray(Object[] args, VariableManager vm) {
		AssocArray aa = new AssocArray(false);
		aa.clear();
		//aa.useLinkedHashMap();
		aa.useMapType(AssocArray.MT_TREE);
		String subsep = toAwkString(vm.getSUBSEP());
		int cnt = 0;
		for (int i = 1; i <= args.length; ++i) {
			Object o = args[i - 1];
			if (o instanceof AssocArray) {
				AssocArray arr = (AssocArray) o;
				for (Object key : arr.keySet()) {
					aa.put("" + i + subsep + key, arr.get(key));
				}
			} else {
				aa.put("" + i, o);
			}
			//aa.put(args[i], args[i+1]);
			++cnt;
		}
		return aa;
	}*/

	// RMP
	private Object copyArray(Object[] args) {
		AssocArray aaTarget = (AssocArray) args[0];
		AssocArray aaSource = (AssocArray) args[1];
		aaTarget.clear();
		for (Object o : aaSource.keySet()) {
			aaTarget.put(o, aaSource.get(o));
		}
		return aaTarget;
	}

    // RMP
    private Object cloneArray(Object[] args) {
        AssocArray aaTarget = new AssocArray(false, getVm());
        AssocArray aaSource = (AssocArray) args[0];
        aaTarget.useMapType(aaSource.getMapType());
        //aaTarget.clear();
        for (Object o : aaSource.keySet()) {
            aaTarget.put(o, aaSource.get(o));
        }
        return aaTarget;
    }

	// RMP
    private Object toInteger(Object arg, VariableManager vm) {
        if (arg instanceof AssocArray) {
            throw new IllegalArgumentException("Cannot deduce integer value from an associative array.");
        }
        // RMP
        return (int)JRT.toDouble(arg);
        
//        if (arg instanceof Number) {
//            return ((Number) arg).intValue();
//        }
//
//        // otherwise, a string
//
//        try {
//            String str = toAwkString(arg);
//            double d = Double.parseDouble(str);
//            return (int)d;
//        } catch (NumberFormatException nfe) {
//            // RMP
//            return (int)0;
//        }
    }
    
	private Object toDouble(Object arg, VariableManager vm) {
		if (arg instanceof AssocArray) {
			throw new IllegalArgumentException("Cannot deduce double value from an associative array.");
		}
		
		// RMP
		return JRT.toDouble(arg);
		
//		if (arg instanceof Number) {
//			return ((Number) arg).doubleValue();
//		}
//
//		// otherwise, a string
//
//		try {
//			String str = toAwkString(arg);
//			double d = Double.parseDouble(str);
//			return d;
//		} catch (NumberFormatException nfe) {
//		    // RMP
//			return (double)0;
//		}
	}

	// RMP
	private String toString(Object arg, VariableManager vm) {
		if (arg instanceof AssocArray) {
			return ((AssocArray) arg).mapString();
		} else {
			return toAwkString(arg);
		}
	}

	private Object timeout(int ms) {
		if (ms <= 0) {
			throw new IllegalAwkArgumentException("Timeout requires a positive # argument, not " + ms + ".");
		}
		waitInt = ms;
		return timeoutBlocker;
	}

	private String version(Object obj) {
		if (obj instanceof AssocArray) {
			return ((AssocArray) obj).getMapVersion();
		} else {
			Class<?> cls = (Class<?>) obj.getClass();
			return cls.getPackage().getSpecificationVersion();
		}
	}

	private String date() {
		dateObj.setTime(System.currentTimeMillis());
		// RMP
        dateFormat.applyPattern(getVm().getDATEFMT().toString());
        return dateFormat.format(dateObj);
	}

	private String date(String formatString) {
		dateObj.setTime(System.currentTimeMillis());
		dateFormat.applyPattern(formatString);
		return dateFormat.format(dateObj);
	}

    // RMP
    private String convertDate(Object[] args) {
        if ((args.length < 2) || (args.length > 3)){
            throw new IllegalAwkArgumentException("Invalid number of arguments for convertDate()");
        }
        dateFormat.applyPattern(args[1].toString());
        try {
            dateObj.setTime(dateFormat.parse(args[0].toString()).getTime());
        } catch (ParseException e) {
            throw new IllegalAwkArgumentException("Invalid date in convertDate()");
        }
        String pattern = getVm().getDATEFMT().toString();
        if (args.length == 3) {
            pattern = args[2].toString();
        }
        dateFormat.applyPattern(pattern);
        return dateFormat.format(dateObj);
    }
    
    // RMP
    private Object subArray(Object[] args) {
        AssocArray aaTarget = (AssocArray)args[0];
        String index = toAwkString(args[1]) + getVm().getSUBSEP();
        AssocArray aaSource = (AssocArray)args[2];
        
        aaTarget.clear();
        for (Object o : aaSource.keySet()) {
            String s = o.toString();
            if (s.startsWith(index)) {
                aaTarget.put(s.substring(index.length()), aaSource.get(o));
            }
        }
        
        return aaTarget;
    }

}
