package com.jagacy.totalAccess.util;

import java.util.Properties;

import javax.xml.XMLConstants;

import org.jawk.jrt.AssocArray;
import org.jawk.jrt.AwkRuntimeException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;

public class Util {
    
    private static AssocArray myNs = null;
    
    public static Object get(AssocArray aa, Object key) {
        if (!aa.isIn(key)) {
            return null;
        }
        
        return aa.get(key);
    }

    public static Object get(AssocArray aa, Object key, Object def) {
        if (!aa.isIn(key)) {
            return def;
        }
        
        return aa.get(key);
    }
    
    public static String getString(AssocArray aa, Object key) {
        if (!aa.isIn(key)) {
            return null;
        }
        
        Object o = aa.get(key);
        
        if (o instanceof AssocArray) {
            throw new AwkRuntimeException("Key " + key + " value is array");
        }
        
        return o.toString();
    }

    public static String getString(AssocArray aa, Object key, String def) {
        if (!aa.isIn(key)) {
            return def;
        }
        
        Object o = aa.get(key);
        
        if (o instanceof AssocArray) {
            throw new AwkRuntimeException("Key " + key + " value is array");
        }
        
        return o.toString();
    }
    
    public static void loadDefProps(Properties props, VariableManager vm) {
        Object o = vm.getDEFPROPS();
        
        if (!(o instanceof AssocArray)) {
            throw new AwkRuntimeException("DEFPROPS is not an array");
        }
        
        AssocArray defprops = (AssocArray) o;
        
        for (Object key : defprops.keySet()) {
            props.put(key, JRT.toAwkString(defprops.get(key), vm.getCONVFMT()));
        }
    }
    
    public static String getNamespaceContext(String prefix, VariableManager vm) {
        if (myNs == null) {
            Object o = vm.getNS();
            if (!(o instanceof AssocArray)) {
                throw new AwkRuntimeException("NS is not an array");
            }
            myNs = (AssocArray) o;
        }
        
        if (prefix.contentEquals("xml")) {
            return XMLConstants.XML_NS_URI;
        }
        
        Object value = null;
        if (myNs.isIn(prefix)) {
            value = myNs.get(prefix);
            
            return JRT.toAwkString(value, vm.getCONVFMT());
        }
        
        return XMLConstants.NULL_NS_URI;
        
    }
}
