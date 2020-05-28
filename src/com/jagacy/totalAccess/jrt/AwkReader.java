package com.jagacy.totalAccess.jrt;

import java.io.IOException;

public interface AwkReader {
    
    //public static final boolean IS_EVAL = true;
    public static final boolean IS_EVAL = false;
    
//    public static final int MESSAGE_COUNT = 25;
//    
//    public static final String MESSAGE = 
//        "This evaluation version of Jagacy Total Access ignores every " + MESSAGE_COUNT + 
//        "th line.  Please purchase a software license at www.jagacy.com.";
    
    public static final int MESSAGE_COUNT = 100;
    
    public static final String MESSAGE = 
        "This evaluation version of Jagacy Total Access only processes the first " + MESSAGE_COUNT + 
        " lines.  Please purchase a software license at www.jagacy.com.";
    
    public boolean fromFilenameList();

    public void setRecordSeparator(String recordSeparator);

    public String readRecord() throws IOException;
    
    public boolean message();
    
    public void close() throws IOException;
}
