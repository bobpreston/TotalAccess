package com.jagacy.totalAccess.jrt;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.jawk.NotImplementedError;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;

public class SysPropsMap implements Map<Object, Object> {

    VariableManager myVm;
    
    public SysPropsMap(VariableManager vm) {
        myVm = vm;
    }
    
    @Override
    public void clear() {
        // Too dangerous.
        throw new NotImplementedError("clear()");
    }

    @Override
    public boolean containsKey(Object key) {
        return System.getProperties().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return System.getProperties().containsValue(JRT.toAwkString(value, myVm.getCONVFMT()));
    }

    @Override
    public Object get(Object key) {
        return System.getProperties().get(key);
    }

    @Override
    public boolean isEmpty() {
        return System.getProperties().isEmpty();
    }

    @Override
    public Object put(Object key, Object value) {
        return System.getProperties().put(key, JRT.toAwkString(value, myVm.getCONVFMT()));
    }

    @Override
    public Object remove(Object key) {
        return System.getProperties().remove(key);
    }

    @Override
    public int size() {
        return System.getProperties().size();
    }

    @Override
    public void putAll(Map<? extends Object, ? extends Object> m) {
        throw new NotImplementedError("putAll()");
        //System.getProperties().putAll(m);
    }

    @Override
    public Set<java.util.Map.Entry<Object, Object>> entrySet() {
        return System.getProperties().entrySet();
    }

    @Override
    public Set<Object> keySet() {
        return System.getProperties().keySet();
    }

    @Override
    public Collection<Object> values() {
        return System.getProperties().values();
    }

}
