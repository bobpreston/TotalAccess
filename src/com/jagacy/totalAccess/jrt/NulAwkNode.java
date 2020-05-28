package com.jagacy.totalAccess.jrt;

import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.w3c.dom.Node;
import com.jagacy.totalAccess.ext.XmlExtension;

public class NulAwkNode extends AwkNode {

    private String myValue = "";

    public NulAwkNode() {
        super((Node)null, false, false, false);
    }
    
    public NulAwkNode(String value) {
        super((Node)null, false, false, false);
        myValue = value;
    }
    
    @Override
    public AssocArray getAttrs(AssocArray aa) {
        if ((aa == null) || !(aa instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("getAttrs", "Invalid argument");
        }
        
        aa.useMapType(AssocArray.MT_LINKED);
        return aa;
    }
    
    @Override
    public String getName() {
        return "\0";
    }
    
    @Override
    public String getValue() {
        return myValue;
    }

    @Override
    public String getXml() {
        return "";
    }
    
    
    // For Velocity:
    
    @Override
    public AssocArray getXmlNodeAttrs(AssocArray aa) {
        if ((aa == null) || !(aa instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("getXmlNodeAttrs", "Invalid argument");
        }
        
        return getAttrs(aa);
    }
    
    @Override
    public String getXmlNodeName() {
        return getName();
    }
    
    @Override
    public String getXmlNodeValue() {
        return getValue();
    }
    
    @Override
    public boolean isXmlNulNode() {
        return true;
    }
    
    @Override
    public AwkNode getXmlNode(String path, boolean isHeader, boolean isIndent) {
        if (path == null) {
            throw new IllegalAwkArgumentException("getXmlNode", "Invalid XPath query argument");
        }
        
        path = path.trim();
        if (path.equals("")) {
            throw new IllegalAwkArgumentException("getXmlNode", "Invalid XPath query argument");
        }
        
        return XmlExtension.NUL_NODE;
    }
    

    @Override
    public AssocArray getXmlNodeArray(String path, AssocArray aa, boolean isHeader, boolean isIndent) {
        if (path == null) {
            throw new IllegalAwkArgumentException("getXmlNodeArray", "Invalid XPath query argument");
        }
        
        path = path.trim();
        if (path.equals("")) {
            throw new IllegalAwkArgumentException("getXmlNodeArray", "Invalid XPath query argument");
        }
        
        if ((aa == null) || !(aa instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("getXmlNodeArray", "Invalid array argument");
        }
        
        aa.useMapType(AssocArray.MT_LINKED);
        
        return aa;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof NulAwkNode) {
            return true;
        }

        return false;
    }
    
    @Override
    public String toString() {
        return myValue;
    }
}
