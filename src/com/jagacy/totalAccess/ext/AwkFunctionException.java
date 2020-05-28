package com.jagacy.totalAccess.ext;

public class AwkFunctionException extends RuntimeException {

    private static final long serialVersionUID = 795763123215219852L;
    
    public AwkFunctionException(String functionName, Throwable t) {
        super("(" + functionName + "): " + t.getMessage(), t);
    }

    public AwkFunctionException(String functionName, String message) {
        super("(" + functionName + "): " + message);
    }

}
