package com.jagacy.totalAccess;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.jawk.Awk;
import org.jawk.ExitException;
import org.jawk.util.AwkParameters;
import org.jawk.util.AwkSettings;

import com.jagacy.totalAccess.util.BetterThreadLocal;

/**
 * Entry point into the parsing, analysis, and execution/compilation
 * of a Jawk script.
 * This entry point is used when Jawk is executed as a stand-alone application.
 * If you want to use Jawk as a library, please use {@see Awk}.
 */
public class TotalAccess {
    
    static {
        String log = System.getProperty("awkLog", "off");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", log);
    }

    public static final String VERSION = "Jagacy Total Access 1.5.8";
    
    private static final boolean IS_DEBUG = Boolean.getBoolean("awkDebug");
    
    private static BetterThreadLocal<OutputStream> OUT_STREAM = new BetterThreadLocal<OutputStream>(); 

    private static BetterThreadLocal<Hashtable<String, String>> VARS = new BetterThreadLocal<Hashtable<String, String>>(); 

    private static BetterThreadLocal<Hashtable<String, Map<Object, Object>>> MAPS = new BetterThreadLocal<Hashtable<String, Map<Object, Object>>>(); 

    public TotalAccess() {
        ;
    }
    
    public void registerInputVariable(String key, String value) {
        Hashtable<String, String> varMap = VARS.get();
        if (varMap == null) {
            varMap = new Hashtable<String, String>();
            VARS.set(varMap);
        }
        
        varMap.put(key, value);
    }
    
    public void registerOutputStream(OutputStream out) {
        OUT_STREAM.set(out);
    }
    
    public static OutputStream getOutputStream() {
        return OUT_STREAM.get();
    }

    public void registerMap(String varName, Map<Object, Object> value) {
        Hashtable<String, Map<Object, Object>> mapMap = MAPS.get();
        if (mapMap == null) {
            mapMap = new Hashtable<String, Map<Object, Object>>();
            MAPS.set(mapMap);
        }
        
        mapMap.put(varName, value);
    }
    
    public static Hashtable<String, Map<Object, Object>> getMaps() {
        return MAPS.get();
    }

    /**
     * An entry point to Jawk that provides the exit code of the script
     * if interpreted or an compiler error status if compiled.
     * If compiled, a non-zero exit status indicates that there
     * was a compilation problem.
     *
     * @param args Command line arguments to the VM,
     *   or JSR 223 scripting interface arguments.
     *
     * @throws IOException upon an IO error.
     * @throws ClassNotFoundException if compilation is requested,
     *   but no compilation implementation class is found.
     * @throws ExitException a specific exit code is requested.
     */
    // RMP
    public void invoke(String[] args)
            throws IOException, ClassNotFoundException, ExitException
    {
        AwkParameters parameters = new AwkParameters(TotalAccess.class, null); // null = NO extension description ==> require AWK script
        AwkSettings settings = parameters.parseCommandLineArguments(args);
        Awk awk = new Awk();
        awk.invoke(settings);
    }

    public void invoke(String script, String ...varFiles)
            throws IOException, ClassNotFoundException, ExitException {
        
        List<String> args = new ArrayList<String>();
        
        if (script == null) {
            throw new IllegalArgumentException("Invalid script");
        }
        
        script = script.trim();
        if (script.equals("")) {
            throw new IllegalArgumentException("Invalid script");
        }
        
        args.add("-f");
        args.add(script);
        
        Hashtable<String, String> varMap = VARS.get();
        if (varMap != null) {
            for (Object key : varMap.keySet()) {
                if (key == null) {
                    throw new IllegalArgumentException("Invalid variable name");
                }
                key = key.toString().trim();
                if (key.equals("")) {
                    throw new IllegalArgumentException("Invalid variable name");
                }
                args.add("-v");
                args.add(key + "=" + varMap.get(key));
            }
        }
        
        for (String i : varFiles) {
            if (i == null) {
                throw new IllegalArgumentException("Invalid varFile");
            }
            if (i.trim().equals("")) {
                throw new IllegalArgumentException("Invalid varFile");
            }
            args.add(i);
        }
        
        AwkParameters parameters = new AwkParameters(TotalAccess.class, null);
        AwkSettings settings = parameters.parseCommandLineArguments(args.toArray(new String[0]));
        Awk awk = new Awk();
        awk.invoke(settings);
    }
	/**
	 * The entry point to Jawk for the VM.
	 * <p>
	 * The main method is a simple call to the invoke method.
	 * The current implementation is basically as follows:
	 * <blockquote>
	 * <pre>
	 * System.exit(invoke(args));
	 * </pre>
	 * </blockquote>
	 * </p>
	 *
	 * @param args Command line arguments to the VM.
	 *
	 */
	public static void main(String[] args) {
        try {
            new TotalAccess().invoke(args);
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                if (t.getCause() != null) {
                    t = t.getCause();
                }
            }
            
            if (IS_DEBUG) {
                t.printStackTrace();
            } else {
                String ex = t + "";
                if (ex.contains("jawk") || ex.contains("totalAccess")) {
                    System.err.println("ERROR: " + t.getMessage());
                } else {
                    System.err.println("ERROR: " + ex);
                }
            }
            System.exit(1);
        }
	}
}

