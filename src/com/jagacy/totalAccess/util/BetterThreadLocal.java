package com.jagacy.totalAccess.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

// RMP
public class BetterThreadLocal<A> {
    
    private Map<Thread, A> map = Collections.synchronizedMap(new WeakHashMap());
    
    public A get() {
        return map.get(Thread.currentThread());
    }
    
    public void set(A a) {
        if (a == null) {
            map.remove(Thread.currentThread());
        } else {
            map.put(Thread.currentThread(), a);
        }
    }

}
