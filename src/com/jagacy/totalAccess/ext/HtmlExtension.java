package com.jagacy.totalAccess.ext;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.xmlbeans.impl.common.ReaderInputStream;
import org.jawk.NotImplementedError;
import org.jawk.ext.AbstractExtension;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.tidy.Tidy;
import org.xml.sax.InputSource;

import com.jagacy.totalAccess.jrt.AwkNode;
import com.jagacy.totalAccess.jrt.InputContext;
import com.jagacy.totalAccess.jrt.NulAwkNode;
import com.jagacy.totalAccess.util.Util;

public class HtmlExtension extends AbstractExtension {

    public static final NulAwkNode NUL_NODE = new NulAwkNode();

    private static Map<String, XPathExpression> myExpressionMap = new Hashtable<String, XPathExpression>();

    private static XPath myXpath = null;
    
    private static XPathExpression myTextXPath = null;


    private static XPathExpression getExpression(String path, final VariableManager vm) throws Exception {
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
        return "HTML/HTTP support";
    }

    @Override
    public String[] extensionKeywords() {
        return new String[] { "createHtmlNode", "createHtmlNodeArray", "loadHtmlNode", "loadHtmlNodeArray",
                "loadHttpNode", "loadHttpNodeArray", "encodeUrl", "decodeUrl" };
    }
    
    private static InputStream getHtmlStream(String fileName) throws Exception {
        InputStream in = null;
        
        if (!(new File(fileName).exists())) {
            
            in = HtmlExtension.class.getResourceAsStream(fileName);
            if (in == null) {
                in = HtmlExtension.class.getClassLoader().getResourceAsStream(fileName);
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
    
    private void setRequestProperties(HttpURLConnection connection, AssocArray aa) throws Exception {
        StringBuilder cookies = new StringBuilder();
        
        for (Object o : aa.keySet()) {
            String s = o.toString();
            
            String[] props = s.split("\\.");
            
            if ((props == null) || (props.length < 2) || !props[0].equals("httpHeader")) {
                continue;
            }
            
            Object value = aa.get(s);
            if (value instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value for " + s);
            }
        
            if (props[1].equalsIgnoreCase("Cookie")) {
                if (props.length != 3) {
                    throw new IllegalAwkArgumentException("Invalid cookie for " + s);
                }
                
                if (cookies.length() > 0) {
                    cookies.append("; ");
                }
                
                cookies.append(URLEncoder.encode(props[2], "UTF-8")).append("=")
                    .append(URLEncoder.encode(toAwkString(value), "UTF-8"));
                
            } else {
                connection.addRequestProperty(props[1], toAwkString(value));
            }
        }
        
        if (cookies.length() > 0) {
            connection.addRequestProperty("Cookie", cookies.toString());
        }
    }
    
    private void setBody(HttpURLConnection connection, AssocArray aa) throws Exception {
        StringBuilder sb = new StringBuilder();
        
        for (Object o : aa.keySet()) {
            String s = o.toString();
            
            String[] props = s.split("\\.");
            
            if ((props == null) || (props.length != 2) || !props[0].equals("httpBody")) {
                continue;
            }
            
            Object value = aa.get(s);
            if (value instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value for " + s);
            }
            
            if (sb.length() > 0) {
                sb.append("&");
            }
            
            sb.append(URLEncoder.encode(props[1], "UTF-8")).append("=")
                .append(URLEncoder.encode(toAwkString(value), "UTF-8"));
        }
        
        if (sb.length() == 0) {
            return;
        }
        
        byte[] body = sb.toString().getBytes("UTF-8");
        
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Length", String.valueOf(body.length));
        
        connection.setDoOutput(true);
        connection.getOutputStream().write(body);
    }
    
    
    private static void setRequestProperties(HttpURLConnection connection, Properties p, VariableManager vm) throws Exception {
        StringBuilder cookies = new StringBuilder();
        
        for (Object o : p.keySet()) {
            String s = o.toString();
            
            String[] props = s.split("\\.");
            
            if ((props == null) || (props.length < 2) || !props[0].equals("httpHeader")) {
                continue;
            }
            
            Object value = p.get(s);
            if (value instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value for " + s);
            }
        
            if (props[1].equalsIgnoreCase("Cookie")) {
                if (props.length != 3) {
                    throw new IllegalAwkArgumentException("Invalid cookie for " + s);
                }
                
                if (cookies.length() > 0) {
                    cookies.append("; ");
                }
                
                cookies.append(URLEncoder.encode(props[2], "UTF-8")).append("=")
                    .append(URLEncoder.encode(JRT.toAwkString(value, vm.getCONVFMT()), "UTF-8"));
                
            } else {
                connection.addRequestProperty(props[1], JRT.toAwkString(value, vm.getCONVFMT()));
            }
        }
        
        if (cookies.length() > 0) {
            connection.addRequestProperty("Cookie", cookies.toString());
        }
    }
    
    private static void setBody(HttpURLConnection connection, Properties p, VariableManager vm) throws Exception {
        StringBuilder sb = new StringBuilder();
        
        for (Object o : p.keySet()) {
            String s = o.toString();
            
            String[] props = s.split("\\.");
            
            if ((props == null) || (props.length != 2) || !props[0].equals("httpBody")) {
                continue;
            }
            
            Object value = p.get(s);
            if (value instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value for " + s);
            }
            
            if (sb.length() > 0) {
                sb.append("&");
            }
            
            sb.append(URLEncoder.encode(props[1], "UTF-8")).append("=")
                .append(URLEncoder.encode(JRT.toAwkString(value, vm.getCONVFMT()), "UTF-8"));
        }
        
        if (sb.length() == 0) {
            return;
        }
        
        byte[] body = sb.toString().getBytes("UTF-8");
        
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Content-Length", String.valueOf(body.length));
        
        connection.setDoOutput(true);
        connection.getOutputStream().write(body);
    }
    
    
    public Object invoke(String keyword, Object[] args) {
        if (keyword.equals("createHtmlNode")) {
            if ((args.length < 2) || (args.length > 4)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException(keyword, "Invalid html argument");
            }
            String html = args[0].toString();
            
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
                Tidy tidy = new Tidy();
                tidy.setDocType("strict");
                tidy.setInputEncoding("UTF-8");
                tidy.setOutputEncoding("UTF-8");
                tidy.setTidyMark(false);
                tidy.setXmlOut(true);
                tidy.setShowWarnings(false);
                tidy.setOnlyErrors(true);
                tidy.setQuiet(true);
                tidy.setForceOutput(false); //true);
                //tidy.setWord2000(isWord2000);
                
                
                Document doc = tidy.parseDOM(new BufferedInputStream(
                        new ReaderInputStream(new StringReader(html), "UTF-8")), null);
                XPathExpression expression = getExpression(path, getVm());
                
                Node n = (Node)expression.evaluate(doc, XPathConstants.NODE);
                if (n == null) {
                    return NUL_NODE;
                }
                
                return new AwkNode(n, isHeader, isIndent, false);

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("createHtmlNodeArray")) {
            if ((args.length < 3) || (args.length > 5)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException(keyword, "Invalid html argument");
            }
            String html = args[0].toString();
            
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
                Tidy tidy = new Tidy();
                tidy.setDocType("strict");
                tidy.setInputEncoding("UTF-8");
                tidy.setOutputEncoding("UTF-8");
                tidy.setTidyMark(false);
                tidy.setXmlOut(true);
                tidy.setShowWarnings(false);
                tidy.setOnlyErrors(true);
                tidy.setQuiet(true);
                tidy.setForceOutput(false); //true);
                //tidy.setWord2000(isWord2000);
                
                Document doc = tidy.parseDOM(new BufferedInputStream(
                        new ReaderInputStream(new StringReader(html), "UTF-8")), null);
                XPathExpression expression = getExpression(path, getVm());
                
                NodeList nodeList = (NodeList)expression.evaluate(doc, XPathConstants.NODESET);
                if (nodeList == null) {
                    return aa;
                }

                for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                    aa.put(i + 1, new AwkNode(nodeList.item(i), isHeader, isIndent, false));
                }
                return aa;

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("loadHtmlNode")) {
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
            
            InputStream htmlStream = null;
            try {
                Tidy tidy = new Tidy();
                tidy.setDocType("strict");
                tidy.setInputEncoding("UTF-8");
                tidy.setOutputEncoding("UTF-8");
                tidy.setTidyMark(false);
                tidy.setXmlOut(true);
                tidy.setShowWarnings(false);
                tidy.setOnlyErrors(true);
                tidy.setQuiet(true);
                tidy.setForceOutput(false); //true);
                //tidy.setWord2000(isWord2000);
                
                
                htmlStream = getHtmlStream(fileName);
                Document doc = tidy.parseDOM(htmlStream, null);
                XPathExpression expression = getExpression(path, getVm());
                 
                Node n = (Node)expression.evaluate(doc, XPathConstants.NODE);
                if (n == null) {
                    return NUL_NODE;
                }
                
                return new AwkNode(n, isHeader, isIndent, false);

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            } finally {
                if (htmlStream != null) {
                    try {
                        htmlStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        } else if (keyword.equals("loadHtmlNodeArray")) {
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
            
            InputStream htmlStream = null;
            try {
                Tidy tidy = new Tidy();
                tidy.setDocType("strict");
                tidy.setInputEncoding("UTF-8");
                tidy.setOutputEncoding("UTF-8");
                tidy.setTidyMark(false);
                tidy.setXmlOut(true);
                tidy.setShowWarnings(false);
                tidy.setOnlyErrors(true);
                tidy.setQuiet(true);
                tidy.setForceOutput(false); //true);
                //tidy.setWord2000(isWord2000);
                
                
                htmlStream = getHtmlStream(fileName);
                Document doc = tidy.parseDOM(htmlStream, null);
                XPathExpression expression = getExpression(path, getVm());
                
                NodeList nodeList = (NodeList)expression.evaluate(doc, XPathConstants.NODESET);
                if (nodeList == null) {
                    return aa;
                }

                for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                    aa.put(i + 1, new AwkNode(nodeList.item(i), isHeader, isIndent, false));
                }
                return aa;

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            } finally {
                if (htmlStream != null) {
                    try {
                        htmlStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        } else if (keyword.equals("loadHttpNode")) {
            if ((args.length < 2) || (args.length > 4)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }

            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid input array argument");
            }
            AssocArray in = (AssocArray)args[0];
            
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
            
            
            String url = Util.getString(in, "httpUrl", "http://localhost:80");
            url = url.trim();
            if (url.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid httpUrl");
            }
            String requestMethod = Util.getString(in, "httpRequestMethod", "GET");
            requestMethod = requestMethod.trim().toUpperCase();
            //String followRedirects = Util.get(in, "httpFollowRedirects", "false").toString();
            //followRedirects = followRedirects.trim();
            //boolean isFollowRedirects = JRT.toAwkBoolean(followRedirects);
            
            
            // TODO aa check
            
            Object connTimeout = Util.get(in, "httpConnectTimeout", 10000);
            int connectTimeout = 0;
            try {
                connectTimeout = (int)JRT.toDouble(connTimeout);
            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
            Object rdTimeout = Util.get(in, "httpReadTimeout", 10000);
            int readTimeout = 0;
            try {
                readTimeout = (int)JRT.toDouble(rdTimeout);
            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
            boolean isUseCaches = JRT.toAwkBoolean(Util.get(in, "httpUseCaches", 0));
            boolean isWord2000 = JRT.toAwkBoolean(Util.get(in, "word2000", 0));

            
            try {
                
                URL u = new URL(url);
                HttpURLConnection connection = (HttpURLConnection)u.openConnection();
                connection.setRequestMethod(requestMethod);
                //connection.setFollowRedirects(isFollowRedirects);
                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                connection.setUseCaches(isUseCaches);
            
                setRequestProperties(connection, in);
                setBody(connection, in);
            
                connection.connect();
            
                int responseCode = connection.getResponseCode();
                if ((responseCode < 200) || (responseCode >= 300)) {
                    return new NulAwkNode(String.valueOf(responseCode));
                }
            
                boolean isXml = false;
                InputSource inputSource = null;
                Document doc = null;
                if (!"application/xml".equals(connection.getContentType()) &&
                        !"text/xml".equals(connection.getContentType())) {
                    Tidy tidy = new Tidy();
                    //tidy.setXHTML(true);
                    tidy.setDocType("strict");
                    tidy.setInputEncoding("UTF-8");
                    tidy.setOutputEncoding("UTF-8");
                    tidy.setTidyMark(false);
                    tidy.setXmlOut(true);
                    tidy.setShowWarnings(false);
                    tidy.setOnlyErrors(true);
                    tidy.setQuiet(true);
                    tidy.setForceOutput(false); //true);
                    tidy.setWord2000(isWord2000);
                
                    doc = tidy.parseDOM(new BufferedInputStream(connection.getInputStream()), null);
                } else {
                    isXml = true;
                    inputSource = new InputSource(new BufferedInputStream(connection.getInputStream()));
                }
                
                XPathExpression expression = getExpression(path, getVm());
                 
                Node n = null;
                if (doc != null) {
                    n = (Node)expression.evaluate(doc, XPathConstants.NODE);
                } else {
                    n = (Node)expression.evaluate(inputSource, XPathConstants.NODE);
                }
                
                connection.getInputStream().close();
                
                if (n == null) {
                    return NUL_NODE;
                }
                
                return new AwkNode(n, isHeader, isIndent, isXml);

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("loadHttpNodeArray")) {
            if ((args.length < 3) || (args.length > 5)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }
            
            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid input array argument");
            }
            AssocArray in = (AssocArray)args[0];
            
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
            
            String url = Util.getString(in, "httpUrl", "http://localhost:80");
            url = url.trim();
            if (url.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid httpUrl");
            }
            String requestMethod = Util.getString(in, "httpRequestMethod", "GET");
            requestMethod = requestMethod.trim().toUpperCase();
            //String followRedirects = Util.get(in, "httpFollowRedirects", "false").toString();
            //followRedirects = followRedirects.trim();
            //boolean isFollowRedirects = JRT.toAwkBoolean(followRedirects);
            
            
            // TODO AssocArray check!
            
            Object connTimeout = Util.get(in, "httpConnectTimeout", 10000);
            int connectTimeout = 0;
            try {
                connectTimeout = (int)JRT.toDouble(connTimeout);
            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
            Object rdTimeout = Util.get(in, "httpReadTimeout", 10000);
            int readTimeout = 0;
            try {
                readTimeout = (int)JRT.toDouble(rdTimeout);
            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
            boolean isUseCaches = JRT.toAwkBoolean(Util.get(in, "httpUseCaches", 0));
            boolean isWord2000 = JRT.toAwkBoolean(Util.get(in, "word2000", 0));

            
            try {
                
                URL u = new URL(url);
                HttpURLConnection connection = (HttpURLConnection)u.openConnection();
                connection.setRequestMethod(requestMethod);
                //connection.setFollowRedirects(isFollowRedirects);
                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                connection.setUseCaches(isUseCaches);
            
                setRequestProperties(connection, in);
                setBody(connection, in);
            
                connection.connect();
            
                int responseCode = connection.getResponseCode();
                if ((responseCode < 200) || (responseCode >= 300)) {
                    aa.put(1, new NulAwkNode(String.valueOf(responseCode)));
                    return aa;
                }
            
                boolean isXml = false;
                InputSource inputSource = null;
                Document doc = null;
                if (!"application/xml".equals(connection.getContentType()) &&
                        !"text/xml".equals(connection.getContentType())) {
                    Tidy tidy = new Tidy();
                    //tidy.setXHTML(true);
                    tidy.setDocType("strict");
                    tidy.setInputEncoding("UTF-8");
                    tidy.setOutputEncoding("UTF-8");
                    tidy.setTidyMark(false);
                    tidy.setXmlOut(true);
                    tidy.setShowWarnings(false);
                    tidy.setOnlyErrors(true);
                    tidy.setQuiet(true);
                    tidy.setForceOutput(false); //true);
                    tidy.setWord2000(isWord2000);
                
                    doc = tidy.parseDOM(new BufferedInputStream(connection.getInputStream()), null);
                } else {
                    isXml = true;
                    inputSource = new InputSource(new BufferedInputStream(connection.getInputStream()));
                }
                
                XPathExpression expression = getExpression(path, getVm());
                 
                NodeList nodeList = null;
                if (doc != null) {
                    nodeList = (NodeList)expression.evaluate(doc, XPathConstants.NODESET);
                } else {
                    nodeList = (NodeList)expression.evaluate(inputSource, XPathConstants.NODESET);
                }
                
                connection.getInputStream().close();
                
                if (nodeList == null) {
                    return aa;
                }

                for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                    aa.put(i + 1, new AwkNode(nodeList.item(i), isHeader, isIndent, isXml));
                }
                return aa;

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("encodeUrl")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String)) {
                throw new IllegalAwkArgumentException(keyword, "Argument is not a string");
            }
            try {
                return URLEncoder.encode(args[0].toString(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("decodeUrl")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String)) {
                throw new IllegalAwkArgumentException(keyword, "Argument is not a string");
            }
            try {
                return URLDecoder.decode(args[0].toString(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else {
            throw new NotImplementedError(keyword);
        }
        // return null;

    }

    public static void open(final VariableManager vm, InputContext<List<AwkNode>> context)
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

        String query = context.myProps.getProperty("httpQuery", "/");
        String header = context.myProps.getProperty("httpHeader", "false");
        String indent = context.myProps.getProperty("httpIndent", "false");
        
        query = query.trim();
        if (query.equals("")) {
            throw new IllegalAwkArgumentException("Invalid httpQuery");
        }
        
        String url = context.myProps.getProperty("httpUrl", "http://localhost:80");
        url = url.trim();
        if (url.equals("")) {
            throw new IllegalAwkArgumentException("Invalid httpUrl");
        }
        
        header = header.trim();
        indent = indent.trim();
        boolean isHeader = header.equals("true") || header.equals("1");
        boolean isIndent = indent.equals("true") || indent.equals("1");

        String requestMethod = context.myProps.getProperty("httpRequestMethod", "GET");
        requestMethod = requestMethod.trim().toUpperCase();
        //String followRedirects = context.myProps.getProperty("httpFollowRedirects", "false");
        //followRedirects = followRedirects.trim();
        //boolean isFollowRedirects = followRedirects.equals("true") || followRedirects.equals("1");
        String connTimeout = context.myProps.getProperty("httpConnectTimeout", "10000");
        connTimeout = connTimeout.trim();
        int connectTimeout = Integer.decode(connTimeout);
        String rdTimeout = context.myProps.getProperty("httpReadTimeout", "10000");
        rdTimeout = rdTimeout.trim();
        int readTimeout = Integer.decode(connTimeout);
        String useCaches = context.myProps.getProperty("httpUseCaches", "false");
        useCaches = useCaches.trim();
        boolean isUseCaches = useCaches.equals("true") || useCaches.equals("1");
        

        String word2000 = context.myProps.getProperty("word2000", "false");
        word2000 = word2000.trim().toUpperCase();
        boolean isWord2000 = word2000.equals("true") || word2000.equals("1");
        
        
        URL u = new URL(url); //URLEncoder.encode(url, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection)u.openConnection();
        connection.setRequestMethod(requestMethod);
        //connection.setFollowRedirects(isFollowRedirects);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setUseCaches(isUseCaches);
        
        try {
            setRequestProperties(connection, context.myProps, vm);
            setBody(connection, context.myProps, vm);
        } catch (Exception e) {
            throw new IOException(e);
        }
        
        connection.connect();
        
        int responseCode = connection.getResponseCode();
        if ((responseCode < 200) || (responseCode >= 300)) {
            context.mySource = new ArrayList<AwkNode>();
            context.mySource.add(new NulAwkNode(String.valueOf(responseCode)));
            return;
        }
        
        boolean isXml = false;
        InputSource inputSource = null;
        Document doc = null;
        if (!"application/xml".equals(connection.getContentType()) &&
                !"text/xml".equals(connection.getContentType())) {
            Tidy tidy = new Tidy();
            //tidy.setXHTML(true);
            tidy.setDocType("strict");
            tidy.setInputEncoding("UTF-8");
            tidy.setOutputEncoding("UTF-8");
            tidy.setTidyMark(false);
            tidy.setXmlOut(true);
            tidy.setShowWarnings(false);
            tidy.setOnlyErrors(true);
            tidy.setQuiet(true);
            tidy.setForceOutput(false); //true);
            tidy.setWord2000(isWord2000);
        
            doc = tidy.parseDOM(new BufferedInputStream(connection.getInputStream()), null);
        } else {
            isXml = true;
            inputSource = new InputSource(new BufferedInputStream(connection.getInputStream()));
        }
        
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

        NodeList nodeList = null;
        try {
            if (doc != null) {
                nodeList = (NodeList) xpath.evaluate(query, doc, XPathConstants.NODESET);
            } else {
                nodeList = (NodeList) xpath.evaluate(query, inputSource, XPathConstants.NODESET);
            }
        } catch (XPathExpressionException e) {
            throw new IOException(e);
        }

        connection.getInputStream().close();
        
        if (nodeList != null) {
            context.mySource = new ArrayList<AwkNode>();
            for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                context.mySource.add(new AwkNode(nodeList.item(i), isHeader, isIndent, isXml));
            }
        }
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

        String query = context.myProps.getProperty("htmlQuery", "/");
        String header = context.myProps.getProperty("htmlHeader", "false");
        String indent = context.myProps.getProperty("htmlIndent", "false");

        query = query.trim();
        if (query.equals("")) {
            throw new IllegalAwkArgumentException("Invalid htmlQuery");
        }
        
        header = header.trim();
        indent = indent.trim();
        boolean isHeader = header.equals("true") || header.equals("1");
        boolean isIndent = indent.equals("true") || indent.equals("1");

        String word2000 = context.myProps.getProperty("word2000", "false");
        word2000 = word2000.trim().toUpperCase();
        boolean isWord2000 = word2000.equals("true") || word2000.equals("1");
        
        Tidy tidy = new Tidy();
        tidy.setDocType("strict");
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setTidyMark(false);
        tidy.setXmlOut(true);
        tidy.setShowWarnings(false);
        tidy.setOnlyErrors(true);
        tidy.setQuiet(true);
        tidy.setForceOutput(false); //true);
        tidy.setWord2000(isWord2000);
        
        NodeList nodeList = null;
        InputStream in = null;  
        try {
            in = getHtmlStream(fileName);
            
            Document doc = tidy.parseDOM(in, null);
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
            nodeList = (NodeList) xpath.evaluate(query, doc, XPathConstants.NODESET);
            
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            if (in != null) {
                in.close();
            }
        }

        if (nodeList != null) {
            context.mySource = new ArrayList<AwkNode>();
            for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                Node n = nodeList.item(i);
                context.mySource.add(new AwkNode(n, isHeader, isIndent, false));
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

        String query = context.myProps.getProperty("htmlQuery", "/");
        String header = context.myProps.getProperty("htmlHeader", "false");
        String indent = context.myProps.getProperty("htmlIndent", "false");

        query = query.trim();
        if (query.equals("")) {
            throw new IllegalAwkArgumentException("Invalid htmlQuery");
        }
        
        header = header.trim();
        indent = indent.trim();
        boolean isHeader = header.equals("true") || header.equals("1");
        boolean isIndent = indent.equals("true") || indent.equals("1");

        String word2000 = context.myProps.getProperty("word2000", "false");
        word2000 = word2000.trim().toUpperCase();
        boolean isWord2000 = word2000.equals("true") || word2000.equals("1");
        
        Tidy tidy = new Tidy();
        tidy.setDocType("strict");
        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setTidyMark(false);
        tidy.setXmlOut(true);
        tidy.setShowWarnings(false);
        tidy.setOnlyErrors(true);
        tidy.setQuiet(true);
        tidy.setForceOutput(false); //true);
        tidy.setWord2000(isWord2000);
        
        Document doc = tidy.parseDOM(in, null);
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

        NodeList nodeList = null;
        try {
            nodeList = (NodeList) xpath.evaluate(query, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IOException(e);
        }

        if (nodeList != null) {
            context.mySource = new ArrayList<AwkNode>();
            for (int i = 0, len = nodeList.getLength(); i < len; i++) {
                Node n = nodeList.item(i);
                context.mySource.add(new AwkNode(n, isHeader, isIndent, false));
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

        if (myTextXPath == null) {
            try {
                myTextXPath = getExpression("text()", vm);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
        
        String ifs = vm.getIFS().toString();
        ifs = StringEscapeUtils.unescapeJava(ifs);

        AwkNode node = context.mySource.get(context.myRow++);
        vm.setNODE(node);

        StringBuilder sb = new StringBuilder();
        try {
            Node n = (Node)myTextXPath.evaluate(node.getNode(), XPathConstants.NODE);
            String text = "";
            if ((n != null) && (n.getNodeValue() != null)) {
                text = n.getNodeValue();
            }
            sb.append(node.getName()).append("=").append(text);
        } catch (XPathExpressionException e) {
            throw new IOException(e);
        }

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
    
    
    static {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509ExtendedTrustManager() {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] xcs, String string, Socket socket) throws CertificateException {

                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] xcs, String string, Socket socket) throws CertificateException {

                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {

                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {

                    }

                }
            };

        try {
            
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Create all-trusting host name verifier
//        HostnameVerifier allHostsValid = new HostnameVerifier() {
//            @Override
//            public boolean verify(String hostname, SSLSession session) {
//                return true;
//            }
//        };
//        // Install the all-trusting host verifier
//        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }
}
