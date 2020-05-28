package com.jagacy.totalAccess.jrt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.jawk.jrt.VariableManager;

import com.jagacy.totalAccess.ext.RestExtension;
import com.jagacy.totalAccess.util.Util;

public class JsonReader implements AwkReader {
    
    private boolean myFromFilenameList = false;
    private VariableManager myVm = null;
    private int myMessageCount = 0;
    private InputStream myIn = null;
    private InputContext<List<AwkNode>> myContext = new InputContext<List<AwkNode>>();
    
    public JsonReader(String fileName, VariableManager vm, boolean isFromFileList) throws IOException {
        myFromFilenameList = isFromFileList;
        myVm = vm;
        
        Util.loadDefProps(myContext.myProps, vm);
        
        if (!(new File(fileName).exists())) {
            
            myIn = getClass().getResourceAsStream(fileName);
            if (myIn == null) {
                myIn = getClass().getClassLoader().getResourceAsStream(fileName);
            }
            if (myIn == null) {
                myIn = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(fileName);
            }
            if (myIn == null) {
                myIn = ClassLoader.getSystemClassLoader()
                    .getResourceAsStream(fileName);
            }
            
            if (myIn == null) {
                throw new FileNotFoundException(fileName);
            }
            
            myIn = new BufferedInputStream(myIn);
            
            RestExtension.open(myIn, vm, myContext);
        } else {
            RestExtension.open(fileName, vm, myContext);
        }
    }

    public JsonReader(InputStream in, VariableManager vm) throws IOException {
        myFromFilenameList = false;
        
        Util.loadDefProps(myContext.myProps, vm);
        
        in = new BufferedInputStream(in);
        // stdin
//        myIn = in;
        myVm = vm;
        RestExtension.open(in, vm, myContext);
    }
    
    @Override
    public void close() throws IOException {
        RestExtension.close(myContext);
        if (myIn != null) {
            myIn.close();
            myIn = null;
        }
    }

    @Override
    public boolean fromFilenameList() {
        return myFromFilenameList;
    }

    @Override
    public boolean message() {
        if (IS_EVAL) {
            if ((myMessageCount++ % MESSAGE_COUNT) == 0) {
                if (myMessageCount == 1) {
                    System.err.println(MESSAGE);
                }
                return myMessageCount != 1;
            }
        }
        
        return false;
    }

    @Override
    public String readRecord() throws IOException {
        if (message()) {
            //line = readRecord();
            return null;
        }
        
        String line = RestExtension.getLine(myVm, myContext);
        return line;
    }

    @Override
    public void setRecordSeparator(String recordSeparator) {
        ;
    }

}
