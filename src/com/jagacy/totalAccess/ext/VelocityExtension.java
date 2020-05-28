package com.jagacy.totalAccess.ext;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.Properties;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jawk.NotImplementedError;
import org.jawk.ext.AbstractExtension;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;

import com.jagacy.totalAccess.TotalAccess;
import com.jagacy.totalAccess.jrt.AwkNode;

public class VelocityExtension extends AbstractExtension {

    @Override
    public String getExtensionName() {
        return "Velocity support";
    }

    @Override
    public String[] extensionKeywords() {
        return new String[] { "mergeVelocityTemplate", "unescapeXml", "escapeXml", "unescapeHtml", "escapeHtml" };
    }

    @Override
    public Object invoke(String keyword, Object[] args) {
        // templateName, context, [outName], [initProps]. 
        if (keyword.equals("mergeVelocityTemplate")) {
            if ((args.length < 2) || (args.length > 4)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid number of arguments");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException(keyword, "Invalid template name");
            }
            String templateName = args[0].toString().trim();
            
            if (templateName.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid template name");
            }
            
            if (!(args[1] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException(keyword, "Invalid array");
            }
            AssocArray aa = (AssocArray)args[1];
            
            String outName = null;
            if (args.length >= 3) {
                if (args[2] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid out name");
                }
                outName = args[2].toString().trim();
                if (outName.equals("")) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid out name");
                }
            }
            
            AssocArray initProps = null;
            if (args.length == 4) {
                if (!(args[3] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid init props");
                }
                initProps = (AssocArray)args[3];
            }

            VelocityEngine ve = new VelocityEngine();
            if (initProps == null) {
                ve.init();
            } else {
                Properties props = new Properties();
                for (Object o : initProps.keySet()) {
                    String key = o.toString();
                    props.put(key, initProps.get(key).toString());
                }
                ve.init(props);
            }
            
            VelocityContext context = new VelocityContext();
            for (Object o : aa.keySet()) {
                String key = o.toString();
                context.put(key, aa.get(key));
            }
            
            Template t = ve.getTemplate(templateName);
            
            if (outName == null) {
                StringWriter writer = new StringWriter();
                t.merge(context, writer);
                
                return writer.toString();
            } else if (outName.equals(":stream:")) {
                OutputStream out = TotalAccess.getOutputStream();
                if (out == null) {
                    throw new IllegalAwkArgumentException(keyword, "Invalid output :stream:");
                }
                OutputStreamWriter outWriter = new OutputStreamWriter(out);
                t.merge(context, outWriter);
                try {
                    outWriter.flush();
                } catch (IOException e) {
                    throw new AwkFunctionException(keyword, e);
                }
            } else {
                FileWriter writer = null;
                try {
                    writer = new FileWriter(outName);
                    t.merge(context, writer);
                } catch (IOException e) {
                    throw new AwkFunctionException(keyword, e);
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
            
            return "";
            
        } else if (keyword.equals("unescapeXml")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Argument is not a string");
            }
            return StringEscapeUtils.unescapeXml(args[0].toString());
        } else if (keyword.equals("escapeXml")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Argument is not a string");
            }
            return StringEscapeUtils.escapeXml11(args[0].toString());
        } else if (keyword.equals("unescapeHtml")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Argument is not a string");
            }
            return StringEscapeUtils.unescapeHtml4(args[0].toString());
        } else if (keyword.equals("escapeHtml")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String) && !(args[0] instanceof AwkNode)) {
                throw new IllegalAwkArgumentException(keyword, "Argument is not a string");
            }
            return StringEscapeUtils.escapeHtml4(args[0].toString());
        } else {
            throw new NotImplementedError(keyword);
        }
        //return null;
    }

}
