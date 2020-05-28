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
 * An example excel servlet.
 * 
 * @author Bob Preston
 * 
 * To use this in your web application:
 * 
 * 1) Copy totalAccess.jar and this file into your web application.
 * 2) Copy csv2excel.awk and real_estate_basic.csv to the WEB-INF/totalAccess
 *    directory in your web application.
 * 3) Add the following to web.xml:
 *         <servlet>
 *              <servlet-name>csv2excel</servlet-name>
 *              <servlet-class>
 *                  com.jagacy.totalAccess.servlet.ExcelServlet
 *              </servlet-class>
 *          </servlet>
 *
 *          <servlet-mapping>
 *              <servlet-name>csv2excel</servlet-name>
 *              <url-pattern>/csv2excel</url-pattern>
 *          </servlet-mapping>
 *
 * 4) Start your web application.
 * 5) Type one of the following into your web browser:
 *      http://localhost:8080/<webapp>/csv2excel?ext=xls
 *      http://localhost:8080/<webapp>/csv2excel?ext=xlsx
 */

public class ExcelServlet extends HttpServlet {
    private static final long serialVersionUID = -2122149597586470902L;
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        OutputStream output;
        boolean attachment;

        output = null;
        attachment = false;
        
        String path = request.getServletContext().getRealPath("/WEB-INF/totalAccess");
        
        String ext = request.getParameter("ext");
        if (!"xls".equals(ext) && !"xlsx".equals(ext)) {
            ext = "xls";
        }
        
        try {
            // Prepare:
            //int contentLength = input.available();
            
            String fileName = "real_estate_basic." + ext;

            String contentType = "application/vnd.ms-excel";
            if (fileName.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            
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
            totalAccess.registerInputVariable("excelFileName", ":stream:." + ext);
            totalAccess.registerOutputStream(output);
            try {
                totalAccess.invoke(path + "/csv2excel.awk", path + "/real_estate_basic.csv");
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
