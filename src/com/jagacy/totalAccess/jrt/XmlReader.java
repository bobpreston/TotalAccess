package com.jagacy.totalAccess.jrt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.jawk.jrt.VariableManager;

import com.jagacy.totalAccess.ext.XmlExtension;
import com.jagacy.totalAccess.util.Util;

public class XmlReader implements AwkReader {
    
    private boolean myFromFilenameList = false;
    private VariableManager myVm = null;
    private int myMessageCount = 0;
    private InputStream myIn = null;
    private InputContext<List<AwkNode>> myContext = new InputContext<List<AwkNode>>();
    
    public XmlReader(String fileName, VariableManager vm, boolean isFromFileList) throws IOException {
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
            
            XmlExtension.open(myIn, vm, myContext);
        } else {
            XmlExtension.open(fileName, vm, myContext);
        }
    }

    public XmlReader(InputStream in, VariableManager vm) throws IOException {
        myFromFilenameList = false;
        
        Util.loadDefProps(myContext.myProps, vm);
        
        in = new BufferedInputStream(in);
        // stdin
//        myIn = in;
        myVm = vm;
        XmlExtension.open(in, vm, myContext);
    }
    
    @Override
    public void close() throws IOException {
        XmlExtension.close(myContext);
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
        
        String line = XmlExtension.getLine(myVm, myContext);
        return line;
    }

    @Override
    public void setRecordSeparator(String recordSeparator) {
        ;
    }

}
