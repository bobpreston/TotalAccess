package com.jagacy.totalAccess.servlet;

import java.io.OutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jagacy.totalAccess.TotalAccess;

/*****************************************************************
 * 
 * An example XML servlet.
 * 
 * @author Bob Preston
 * 
 * To use this in your web application:
 * 
 * 1) Copy totalAccess.jar and this file into your web application.
 * 2) Copy xml2html.awk, xml2html.vm and nutrition.xml to the WEB-INF/totalAccess
 *    directory in your web application.
 * 3) Add the following to web.xml:
 *         <servlet>
 *              <servlet-name>xml2html</servlet-name>
 *              <servlet-class>
 *                  com.jagacy.totalAccess.servlet.XmlServlet
 *              </servlet-class>
 *          </servlet>
 *
 *          <servlet-mapping>
 *              <servlet-name>xml2html</servlet-name>
 *              <url-pattern>/xml2html</url-pattern>
 *          </servlet-mapping>
 *
 * 4) Start your web application.
 * 5) Type the following into your web browser:
 *      http://localhost:8080/<webapp>/xml2html
 */

public class XmlServlet extends HttpServlet {
    private static final long serialVersionUID = -2122149597586470901L;

    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        OutputStream output;
        boolean attachment;

        output = null;
        attachment = false;
        
        String path = request.getServletContext().getRealPath("/WEB-INF/totalAccess");
        
        try {
            // Prepare:
            //int contentLength = input.available();
            String fileName = "food.html";
            String contentType = "text/html";
            String disposition = attachment ? "attachment" : "inline";

            // Init servlet response:
            response.setHeader("cache-control", "no-cache, max-age=0");
            response.setHeader("pragma", "no-cache");
            response.setHeader("expires", "-1");
            response.setDateHeader("Last-Modified", System.currentTimeMillis());
            //response.setContentLength(contentLength);
            response.setContentType(contentType);
            response.setHeader("Content-disposition", disposition
                    + "; filename=\"" + fileName + "\"");

            output = response.getOutputStream();
            
            // Invoke the Total Access engine:
            TotalAccess totalAccess = new TotalAccess();
            totalAccess.registerInputVariable("htmlFileName", ":stream:");
            totalAccess.registerInputVariable("path", path);
            totalAccess.registerOutputStream(output);
            try {
                totalAccess.invoke(path + "/xml2html.awk", path + "/nutrition.xml");
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }

        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
