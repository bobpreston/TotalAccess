package com.jagacy.totalAccess.ext;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.text.StringEscapeUtils;
import org.jawk.NotImplementedError;
import org.jawk.ext.AbstractExtension;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.jagacy.totalAccess.jrt.AwkNode;
import com.jagacy.totalAccess.jrt.InputContext;
import com.jagacy.totalAccess.jrt.NulAwkNode;
import com.jagacy.totalAccess.util.Util;

public class XmlExtension extends AbstractExtension {

    public static final NulAwkNode NUL_NODE = new NulAwkNode();

    private Map<String, XPathExpression> myExpressionMap = new HashMap<String, XPathExpression>();

    private XPath myXpath = null;

    private XPathExpression getExpression(String path, final VariableManager vm) throws Exception {
        if (myXpath == null) {
            myXpath = XPathFactory.newInstance().newXPath();
            myXpath.setNamespaceContext(new NamespaceContext() {
                public String getNamespaceURI(String prefix) {
                    return Util.getNamespaceContext(prefix, vm);
                }

                // This method isn't necessary for XPath processing.
                public String getPrefix(String uri) {
                    throw new UnsupportedOperationException();
                }

                // This method isn't necessary for XPath processing either.
                public Iterator getPrefixes(String uri) {
                    throw new UnsupportedOperationException();
                }
            });
        } else {
            myXpath.reset();
        }

        XPathExpression expression = myExpressionMap.get(path);
        if (expression == null) {
            expression = myXpath.compile(path);
            myExpressionMap.put(path, expression);
        }

        return expression;
    }

    @Override
    public String getExtensionName() {
        return "XML support";
    }

    @Override
    public String[] extensionKeywords() {
        return new String[] { "getXmlNodeAttrs", "getXmlNodeName", "getXmlNodeValue", "isXmlNulNode", "getXmlNode",
                "getXmlNodeArray", "createXmlNode", "createXmlNodeArray", "loadXmlNode", "loadXmlNodeArray" };
    }
    
    private InputStream getXmlStream(String fileName) throws Exception {
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
            
            in = new BufferedInputStream(in);
            
        } else {
            in = new BufferedInputStream(new FileInputStream(fileName));
        }
        
        return in;
    }

    @Override
    public Object invoke(String keyword, Object[] args) {
        if (keyword.equals("getXmlNodeAttrs")) {
            checkNumArgs(args, 2, keyword);
            if (!(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid node argument");
            }
            AwkNode node = (AwkNode) args[0];
            if (!(args[1] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid array argument");
            }
            AssocArray aa = (AssocArray) args[1];

            return node.getAttrs(aa);

        } else if (keyword.equals("getXmlNodeName")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid node argument");
            }
            AwkNode node = (AwkNode) args[0];

            return node.getName();
        } else if (keyword.equals("getXmlNodeValue")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid node argument");
            }
            AwkNode node = (AwkNode) args[0];

            return node.getValue();
        } else if (keyword.equals("isXmlNulNode")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid node argument");
            }
            AwkNode node = (AwkNode) args[0];

            return node.equals(NUL_NODE) ? 1 : 0;
        } else if (keyword.equals("getXmlNode")) {
            if ((args.length < 2) || (args.length > 4)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (!(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid node argument");
            }
            AwkNode node = (AwkNode) args[0];
            
            if (node.equals(NUL_NODE)) {
                return NUL_NODE;
            }
            
            String path = args[1].toString();
            path = path.trim();
            if (path.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid XPath query argument");
            }
            
            boolean isHeader = false;
            if (args.length >= 3) {
                if (args[2] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isHeader = JRT.toAwkBoolean(args[2]);
            }
            
            boolean isIndent = false;
            if (args.length == 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isIndent = JRT.toAwkBoolean(args[3]);
            }
            
            try {
                XPathExpression expression = getExpression(path, getVm());
                Node n = (Node)expression.evaluate(node.getNode(), XPathConstants.NODE);
                if (n == null) {
                    return NUL_NODE;
                }
                
                return new AwkNode(n, isHeader, isIndent, node.isXml());

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("getXmlNodeArray")) {
            if ((args.length < 3) || (args.length > 5)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (!(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid node argument");
            }
            AwkNode node = (AwkNode) args[0];
            
            if (node.equals(NUL_NODE)) {
                return NUL_NODE;
            }
            
            String path = args[1].toString();
            path = path.trim();
            if (path.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid XPath query argument");
            }
            
            if (!(args[2] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid array argument");
            }
            AssocArray aa = (AssocArray)args[2];
            
            boolean isHeader = false;
            if (args.length >= 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isHeader = JRT.toAwkBoolean(args[3]);
            }
            
            boolean isIndent = false;
            if (args.length == 5) {
                if (args[4] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isIndent = JRT.toAwkBoolean(args[4]);
            }
            
            aa.useMapType(AssocArray.MT_LINKED);
            
            try {
                XPathExpression expression = getExpression(path, getVm());
                NodeList nodeList = (NodeList)expression.evaluate(node.getNode(), XPathConstants.NODESET);
                if (nodeList == null) {
                    return aa;
                }

                for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                    aa.put(i + 1, new AwkNode(nodeList.item(i), isHeader, isIndent, node.isXml()));
                }
                return aa;

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("createXmlNode")) {
            if ((args.length < 2) || (args.length > 4)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException(keyword, "Invalid xml argument");
            }
            String xml = args[0].toString();
            
            String path = args[1].toString();
            path = path.trim();
            if (path.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid XPath query argument");
            }
            
            boolean isHeader = false;
            if (args.length >= 3) {
                if (args[2] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isHeader = JRT.toAwkBoolean(args[2]);
            }
            
            boolean isIndent = false;
            if (args.length == 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isIndent = JRT.toAwkBoolean(args[3]);
            }
            
            try {
                XPathExpression expression = getExpression(path, getVm());
                InputSource inputSource = new InputSource(new StringReader(xml));
                
                Node n = (Node)expression.evaluate(inputSource, XPathConstants.NODE);
                if (n == null) {
                    return NUL_NODE;
                }
                
                return new AwkNode(n, isHeader, isIndent, true);

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("createXmlNodeArray")) {
            if ((args.length < 3) || (args.length > 5)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException(keyword, "Invalid xml argument");
            }
            String xml = args[0].toString();
            
            String path = args[1].toString();
            path = path.trim();
            if (path.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid XPath query argument");
            }
            
            if (!(args[2] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid array argument");
            }
            AssocArray aa = (AssocArray)args[2];
            
            boolean isHeader = false;
            if (args.length >= 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isHeader = JRT.toAwkBoolean(args[3]);
            }
            
            boolean isIndent = false;
            if (args.length == 5) {
                if (args[4] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isIndent = JRT.toAwkBoolean(args[4]);
            }
            
            aa.useMapType(AssocArray.MT_LINKED);
            
            try {
                XPathExpression expression = getExpression(path, getVm());
                InputSource inputSource = new InputSource(new StringReader(xml));
                NodeList nodeList = (NodeList)expression.evaluate(inputSource, XPathConstants.NODESET);
                if (nodeList == null) {
                    return aa;
                }

                for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                    Node n = nodeList.item(i);
                    aa.put(i + 1, new AwkNode(n, isHeader, isIndent, true));
                }
                return aa;

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("loadXmlNode")) {
            if ((args.length < 2) || (args.length > 4)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException(keyword, "Invalid file name argument");
            }
            String fileName = args[0].toString().trim();
            if (fileName.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid file name argument");
            }
            
            String path = args[1].toString();
            path = path.trim();
            if (path.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid XPath query argument");
            }
            
            boolean isHeader = false;
            if (args.length >= 3) {
                if (args[2] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isHeader = JRT.toAwkBoolean(args[2]);
            }
            
            boolean isIndent = false;
            if (args.length == 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isIndent = JRT.toAwkBoolean(args[3]);
            }
            
            InputStream xmlStream = null;
            try {
                XPathExpression expression = getExpression(path, getVm());
                xmlStream = getXmlStream(fileName);
                InputSource inputSource = new InputSource(xmlStream);
                
                Node n = (Node)expression.evaluate(inputSource, XPathConstants.NODE);
                if (n == null) {
                    return NUL_NODE;
                }
                
                return new AwkNode(n, isHeader, isIndent, true);

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            } finally {
                if (xmlStream != null) {
                    try {
                        xmlStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        } else if (keyword.equals("loadXmlNodeArray")) {
            if ((args.length < 3) || (args.length > 5)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException(keyword, "Invalid file name argument");
            }
            String fileName = args[0].toString().trim();
            if (fileName.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid file name argument");
            }
            
            String path = args[1].toString();
            path = path.trim();
            if (path.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid XPath query argument");
            }
            
            if (!(args[2] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid array argument");
            }
            AssocArray aa = (AssocArray)args[2];
            
            boolean isHeader = false;
            if (args.length >= 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isHeader = JRT.toAwkBoolean(args[3]);
            }
            
            boolean isIndent = false;
            if (args.length == 5) {
                if (args[4] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid boolean argument");
                }
                isIndent = JRT.toAwkBoolean(args[4]);
            }
            
            aa.useMapType(AssocArray.MT_LINKED);
            
            InputStream xmlStream = null;
            try {
                XPathExpression expression = getExpression(path, getVm());
                xmlStream = getXmlStream(fileName);
                InputSource inputSource = new InputSource(xmlStream);
                NodeList nodeList = (NodeList)expression.evaluate(inputSource, XPathConstants.NODESET);
                if (nodeList == null) {
                    return aa;
                }

                for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                    Node n = nodeList.item(i);
                    aa.put(i + 1, new AwkNode(n, isHeader, isIndent, true));
                }
                return aa;

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            } finally {
                if (xmlStream != null) {
                    try {
                        xmlStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        } else {
            throw new NotImplementedError(keyword);
        }
        // return null;

    }

    public static void open(String fileName, final VariableManager vm, InputContext<List<AwkNode>> context)
            throws IOException {
        context.mySource = null;

        String it = vm.getIT().toString();

        if (it.indexOf(":") != -1) {
            it = it.substring(it.indexOf(":") + 1);

            String[] props = it.split(",", -1);

            for (String prop : props) {
                String[] keyValue = prop.split("=", -1);
                if (keyValue.length == 0) {
                    ;
                } else if (keyValue.length == 1) {
                    context.myProps.put(keyValue[0], "");
                } else if (keyValue.length == 2) {
                    context.myProps.put(keyValue[0], StringEscapeUtils.unescapeJava(keyValue[1]));
                } else {
                    throw new IllegalAwkArgumentException("Invalid IT variable=" + it);
                }
            }
        }

        vm.setNODE(NUL_NODE);

        String query = context.myProps.getProperty("xmlQuery", "/");
        String header = context.myProps.getProperty("xmlHeader", "false");
        String indent = context.myProps.getProperty("xmlIndent", "false");

        query = query.trim();
        if (query.equals("")) {
            throw new IllegalAwkArgumentException("Invalid xmlQuery");
        }
        
        header = header.trim();
        indent = indent.trim();
        boolean isHeader = header.equals("true") || header.equals("1");
        boolean isIndent = indent.equals("true") || indent.equals("1");

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return Util.getNamespaceContext(prefix, vm);
            }

            // This method isn't necessary for XPath processing.
            public String getPrefix(String uri) {
                throw new UnsupportedOperationException();
            }

            // This method isn't necessary for XPath processing either.
            public Iterator getPrefixes(String uri) {
                throw new UnsupportedOperationException();
            }
        });

        InputSource inputSource = new InputSource(fileName);

        NodeList nodeList = null;
        try {
            nodeList = (NodeList) xpath.evaluate(query, inputSource, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IOException(e);
        }

        if (nodeList != null) {
            context.mySource = new ArrayList<AwkNode>();
            for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                Node n = nodeList.item(i);
                context.mySource.add(new AwkNode(n, isHeader, isIndent, true));
            }
        }
    }

    public static void open(InputStream in, final VariableManager vm, InputContext<List<AwkNode>> context)
            throws IOException {
        context.mySource = null;

        String it = vm.getIT().toString();

        if (it.indexOf(":") != -1) {
            it = it.substring(it.indexOf(":") + 1);

            String[] props = it.split(",", -1);

            for (String prop : props) {
                String[] keyValue = prop.split("=", -1);
                if (keyValue.length == 0) {
                    ;
                } else if (keyValue.length == 1) {
                    context.myProps.put(keyValue[0], "");
                } else if (keyValue.length == 2) {
                    context.myProps.put(keyValue[0], StringEscapeUtils.unescapeJava(keyValue[1]));
                } else {
                    throw new IllegalAwkArgumentException("Invalid IT variable=" + it);
                }
            }
        }

        vm.setNODE(NUL_NODE);

        String query = context.myProps.getProperty("xmlQuery", "/");
        String header = context.myProps.getProperty("xmlHeader", "false");
        String indent = context.myProps.getProperty("xmlIndent", "false");

        query = query.trim();
        if (query.equals("")) {
            throw new IllegalAwkArgumentException("Invalid xmlQuery");
        }
        
        header = header.trim();
        indent = indent.trim();
        boolean isHeader = header.equals("true") || header.equals("1");
        boolean isIndent = indent.equals("true") || indent.equals("1");

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return Util.getNamespaceContext(prefix, vm);
            }

            // This method isn't necessary for XPath processing.
            public String getPrefix(String uri) {
                throw new UnsupportedOperationException();
            }

            // This method isn't necessary for XPath processing either.
            public Iterator getPrefixes(String uri) {
                throw new UnsupportedOperationException();
            }
        });

        InputSource inputSource = new InputSource(in);

        NodeList nodeList = null;
        try {
            nodeList = (NodeList) xpath.evaluate(query, inputSource, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IOException(e);
        }

        if (nodeList != null) {
            context.mySource = new ArrayList<AwkNode>();
            for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                Node n = nodeList.item(i);
                context.mySource.add(new AwkNode(n, isHeader, isIndent, true));
            }
        }
    }

    public static String getLine(VariableManager vm, InputContext<List<AwkNode>> context) throws IOException {
        vm.setNODE(NUL_NODE);

        if (context.mySource == null) {
            return null;
        }

        if (context.myRow >= context.mySource.size()) {
            return null;
        }

        String ifs = vm.getIFS().toString();
        ifs = StringEscapeUtils.unescapeJava(ifs);

        AwkNode node = context.mySource.get(context.myRow++);
        vm.setNODE(node);

        StringBuilder sb = new StringBuilder();
        sb.append(node.getName());

        AssocArray attrs = new AssocArray(true, vm);
        node.getAttrs(attrs);

        for (Object key : attrs.keySet()) {
            sb.append(ifs);
            sb.append(key).append("=").append(attrs.get(key));
        }

        return sb.toString();
    }

    public static void close(InputContext<List<AwkNode>> context) throws IOException {
        if (context.mySource == null) {
            return;
        }
        context.mySource = null;
    }
}
