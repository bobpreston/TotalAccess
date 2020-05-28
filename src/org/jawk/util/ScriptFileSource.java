package org.jawk.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents one AWK-script file content source.
 */
public class ScriptFileSource extends ScriptSource {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptFileSource.class);

	private String filePath;
	private Reader fileReader;
	private InputStream fileInputStream;

	public ScriptFileSource(String filePath) {
		super(filePath, null, filePath.endsWith(".ai"));

		this.filePath = filePath;
		this.fileReader = null;
		this.fileInputStream = null;
	}

	public String getFilePath() {
		return filePath;
	}
	
	// RMP

    private Reader createReader(String fileName) throws IOException {
        Reader reader = null;
        if (!(new File(fileName).exists())) {
            
            InputStream in = getClass().getResourceAsStream(fileName);
            if (in == null) {
                in = getClass().getClassLoader().getResourceAsStream(fileName);
            }
            if (in == null) {
                in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(fileName);
            }
            if (in == null) {
                in = ClassLoader.getSystemClassLoader()
                    .getResourceAsStream(fileName);
            }
            
            if (in == null) {
                throw new FileNotFoundException(fileName);
            }
            
            reader = new InputStreamReader(in);
            
        } else {
            reader = new FileReader(fileName);
        }
        
        reader = new BufferedReader(reader);
        
        return reader;
    }
    
    
    private InputStream createInputStream(String fileName) throws IOException {
        InputStream in = null;
        if (!(new File(fileName).exists())) {
            
            in = getClass().getResourceAsStream(fileName);
            if (in == null) {
                in = getClass().getClassLoader().getResourceAsStream(fileName);
            }
            if (in == null) {
                in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(fileName);
            }
            if (in == null) {
                in = ClassLoader.getSystemClassLoader()
                    .getResourceAsStream(fileName);
            }
            
            if (in == null) {
                throw new FileNotFoundException(fileName);
            }
            
        } else {
            in = new FileInputStream(fileName);
        }
        
        in = new BufferedInputStream(in);
        
        return in;
    }
    
    
	@Override
	public Reader getReader() {

		if ((fileReader == null) && !isIntermediate()) {
			try {
			    // RMP
				//fileReader = new FileReader(filePath);
                fileReader = createReader(filePath);
			} catch (IOException ex) {
			    // RMP
				//LOG.error("Failed to open script source for reading: " + filePath, ex);
			    throw new RuntimeException(ex);
			}
		}

		return fileReader;
	}

	@Override
	public InputStream getInputStream() {

		if ((fileInputStream == null) && isIntermediate()) {
			try {
			    // RMP
				//fileInputStream = new FileInputStream(filePath);
                fileInputStream = createInputStream(filePath);
			} catch (IOException ex) {
                // RMP
				//LOG.error("Failed to open script source for reading: " + filePath, ex);
                throw new RuntimeException(ex);
			}
		}

		return fileInputStream;
	}
}
