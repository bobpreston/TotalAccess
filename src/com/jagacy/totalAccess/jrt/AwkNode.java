package com.jagacy.totalAccess.jrt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.text.StringEscapeUtils;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.jagacy.totalAccess.ext.AwkFunctionException;
import com.jagacy.totalAccess.ext.XmlExtension;

public class AwkNode {

    private Node myNode;
    private boolean myIsHeader;
    private boolean myIsIndent;
    
    private String myXml = null;
    
    // HTML for META tag.
    private boolean myIsXml = true;

    private Transformer myTransformer = null;
    private DocumentBuilder myBuilder = null;
    private XPath myXpath = null;

    
    public AwkNode(Node node, boolean isHeader, boolean isIndent, boolean isXml) {
        myNode = node;
        myIsHeader = isHeader;
        myIsIndent = isIndent;
        myIsXml = isXml;
    }

    public AwkNode(String xml, boolean isHeader, boolean isIndent, boolean isXml) throws Exception {
        if (myBuilder == null) {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            myBuilder = builderFactory.newDocumentBuilder();
        } else {
            try {
                myBuilder.reset();
            } catch (UnsupportedOperationException e) {
                ;
            }
        }

//        Document xmlDocument = myBuilder.parse(fileName);
        InputStream in = new ByteArrayInputStream(xml.getBytes());
        Document xmlDocument = null;
        
        try {
            xmlDocument = myBuilder.parse(in);
        } finally {
            if (in != null) {
                in.close();
            }
        }
        
        myNode = xmlDocument.getDocumentElement();
        myIsHeader = isHeader;
        myIsIndent = isIndent;
        myIsXml = isXml;
    }
    
    public boolean isXml() {
        return myIsXml; 
    }

    public AssocArray getAttrs(AssocArray aa) {
        if ((aa == null) || !(aa instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("getAttrs", "Invalid argument");
        }
        
        aa.useMapType(AssocArray.MT_TREE);

        NamedNodeMap nodeMap = myNode.getAttributes();
        if (nodeMap != null) {
            for (int i = 0, len = nodeMap.getLength(); i < len; i++) {
                Node item = nodeMap.item(i);
                if (item != null) {
                    String name = item.getNodeName();
                    if (name == null) {
                        name = "";
                    }
                    String value = item.getNodeValue();
                    if (value == null) {
                        value = "";
                    }
                    aa.put(name, value);
                }
            }
        }

        return aa;
    }

    public String getName() {
        String name = myNode.getNodeName();
        if (name == null) {
            name = "";
        }

        return name;
    }

    public String getValue() {
        String value = myNode.getNodeValue();
        if (value == null) {
            value = "";
        }

        return value;
    }
    
    public Node getNode() {
        return myNode;
    }

    private String nodeToString(Node node) throws TransformerException {
        StringWriter sw = new StringWriter();

        if (myTransformer == null) {
            myTransformer = TransformerFactory.newInstance().newTransformer();
        }

        myTransformer.setOutputProperty(OutputKeys.METHOD, myIsXml ? "xml" : "html");
        myTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        myTransformer.setOutputProperty(OutputKeys.INDENT, myIsIndent ? "yes" : "no");
        myTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, myIsHeader ? "no" : "yes");
        myTransformer.transform(new DOMSource(node), new StreamResult(sw));
        //myTransformer.transform(new SAXSource(node), new StreamResult(sw));
        return sw.toString();
    }

    public String getXml() {

        if (myXml == null) {
            String xml = null;
            try {
                xml = nodeToString(myNode);
            } catch (TransformerException e) {
                throw new AwkFunctionException("getXml", e);
            }
            if (xml == null) {
                xml = "";
            }
            myXml = xml;
        }

        return myXml;
    }
    
    // For Velocity:
    
    public AssocArray getXmlNodeAttrs(AssocArray aa) {
        if ((aa == null) || !(aa instanceof AssocArray)) {
            throw new IllegalAwkArgumentException("getXmlNodeAttrs", "Invalid argument");
        }
        
        return getAttrs(aa);
    }
    
    public String getXmlNodeName() {
        return getName();
    }
    
    public String getXmlNodeValue() {
        return getValue();
    }
    
    public boolean isXmlNulNode() {
        //return equals(XmlExtension.NUL_NODE);
        return false;
    }
    
    
    
    public AwkNode getXmlNode(String path) {
        return getXmlNode(path, false);
    }
    
    public AwkNode getXmlNode(String path, boolean isHeader) {
        return getXmlNode(path, isHeader, false);
    }
    
    public AwkNode getXmlNode(String path, boolean isHeader, boolean isIndent) {
        if (path == null) {
            throw new IllegalAwkArgumentException("getXmlNode", "Invalid XPath query argument");
        }
        
        path = path.trim();
        if (path.equals("")) {
            throw new IllegalAwkArgumentException("getXmlNode", "Invalid XPath query argument");
        }
        
        try {
            if (myXpath == null) {
                myXpath = XPathFactory.newInstance().newXPath();
            } else {
                myXpath.reset();
            }
            
            Node n = (Node)myXpath.evaluate(path, myNode, XPathConstants.NODE);
            if (n == null) {
                return XmlExtension.NUL_NODE;
            }
            
            return new AwkNode(n, isHeader, isIndent, myIsXml);

        } catch (Exception e) {
            throw new AwkFunctionException("getXmlNode", e);
        }
       
    }
    
    public AssocArray getXmlNodeArray(String path, AssocArray aa) {
        return getXmlNodeArray(path, aa, false);
    }

    
    public AssocArray getXmlNodeArray(String path, AssocArray aa, boolean isHeader) {
        return getXmlNodeArray(path, aa, isHeader, false);
    }
    
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
        
        try {
            if (myXpath == null) {
                myXpath = XPathFactory.newInstance().newXPath();
            } else {
                myXpath.reset();
            }
            
            NodeList nodeList = (NodeList)myXpath.evaluate(path, myNode, XPathConstants.NODESET);
            if (nodeList == null) {
                return aa;
            }

            for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                aa.put(i + 1, new AwkNode(nodeList.item(i), isHeader, isIndent, myIsXml));
            }
            
            return aa;
            
        } catch (Exception e) {
            throw new AwkFunctionException("getXmlNodeArray", e);
        }
    }
    
    public String escapeXmlName() {
        return StringEscapeUtils.escapeXml11(getName());
    }
    
    public String escapeXmlValue() {
        return StringEscapeUtils.escapeXml11(getValue());
    }

    public String escapeHtmlName() {
        return StringEscapeUtils.escapeHtml4(getName());
    }
    
    public String escapeHtmlValue() {
        return StringEscapeUtils.escapeHtml4(getValue());
    }
    
    @Override
    public boolean equals(Object o) {
        if ((o instanceof AwkNode) && (((AwkNode)o).myNode != null)) {
            //return myNode.equals(((AwkNode) o).myNode);
            return myNode.isEqualNode(((AwkNode)o).myNode);
        }

        return false;
    }

    @Override
    public String toString() {
        return getXml();
    }
}
