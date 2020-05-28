package com.jagacy.totalAccess.ext;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.text.StringEscapeUtils;
import org.jawk.NotImplementedError;
import org.jawk.ext.AbstractExtension;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.VariableManager;

import com.jagacy.totalAccess.jrt.AwkNode;
import com.jagacy.totalAccess.jrt.DatabaseContext;
import com.jagacy.totalAccess.jrt.InputContext;
import com.jagacy.totalAccess.util.Util;

public class DatabaseExtension extends AbstractExtension {
    
    private static final boolean IS_DEBUG = Boolean.getBoolean("awkDebug");

    private static final Map<String, Connection> CONNECTION_MAP = new Hashtable<String, Connection>();
    private static final Map<String, Integer> CONNECTION_COUNT_MAP = new Hashtable<String, Integer>();

    private static final Map<String, NamedParameterStatement> STATEMENT_MAP = new Hashtable<String, NamedParameterStatement>();
    private static final Map<String, Integer> STATEMENT_COUNT_MAP = new Hashtable<String, Integer>();

    private static final Map<String, ResultSet> RESULTSET_MAP = new Hashtable<String, ResultSet>();

    private SimpleDateFormat myDateFormat = null;

    @Override
    public String getExtensionName() {
        return "Database support";
    }

    @Override
    public String[] extensionKeywords() {
        return new String[] { "openDatabaseQuery", "getDatabaseRow", "queryDatabase", "updateDatabase",
                "closeDatabaseStatement", "loadArray" };
    }

    private String openConnection(AssocArray in) throws SQLException {

        Object t = null;

        boolean isAutoCommit = true;
        t = Util.get(in, "autoCommit");
        if (t != null) {
            String s = t.toString().trim();
            isAutoCommit = s.equals("true") || s.equals("1");
        }

        t = Util.get(in, "databaseUrl");
        if (t == null) {
            throw new IllegalAwkArgumentException("databaseUrl not specified");
        }
        String url = t.toString().trim();
        if (url.equals("")) {
            throw new IllegalAwkArgumentException("databaseUrl not specified");
        }

        String connectionHandle = "db" + "\0" + Thread.currentThread().getId() + "\0" + url + "\0" + isAutoCommit;

        Connection connection = CONNECTION_MAP.get(connectionHandle);
        if (connection == null) {

            t = Util.get(in, "driverClass");
            if (t == null) {
                throw new IllegalAwkArgumentException("driverClass not specified");
            }
            String driverClass = t.toString().trim();
            if (driverClass.equals("")) {
                throw new IllegalAwkArgumentException("driverClass not specified");
            }

            t = Util.get(in, "databaseUserName");
            if (t == null) {
                throw new IllegalAwkArgumentException("databaseUserName not specified");
            }
            String userName = t.toString();

            t = Util.get(in, "databasePassword");
            if (t == null) {
                throw new IllegalAwkArgumentException("databasePassword not specified");
            }
            String password = t.toString();

            Object instance = null;

            try {
                instance = Class.forName(driverClass).newInstance();
            } catch (Exception e) {
                throw new SQLException(e);
            }
            DriverManager.registerDriver((Driver) instance);

            if (IS_DEBUG) System.err.println("Opening connection " + connectionHandle);

            connection = DriverManager.getConnection(url, userName, password);
            connection.setAutoCommit(isAutoCommit);

            CONNECTION_MAP.put(connectionHandle, connection);
        }

        return connectionHandle;
    }

    private void closeConnection(String connectionHandle) throws SQLException {

        Integer cnt = CONNECTION_COUNT_MAP.get(connectionHandle);
        int count = 1;
        if (cnt != null) {
            count = cnt;
        }
        if (count <= 0) {

            if (IS_DEBUG) System.err.println("Closing connection " + connectionHandle);

            CONNECTION_COUNT_MAP.remove(connectionHandle);

            Connection connection = CONNECTION_MAP.get(connectionHandle);
            if (connection != null) {
                connection.close();
                CONNECTION_MAP.remove(connectionHandle);
            }
        }
    }

    private String openStatement(AssocArray in, String sqlKey) throws SQLException {

        String connectionHandle = openConnection(in);

        Object t = Util.get(in, sqlKey);
        if (t == null) {
            throw new IllegalAwkArgumentException(sqlKey + " not specified");
        }
        String sql = t.toString().trim();

        String statementHandle = connectionHandle + "\0" + StringEscapeUtils.escapeJava(sql);

        NamedParameterStatement p = STATEMENT_MAP.get(statementHandle);
        if (p == null) {
            if (IS_DEBUG) System.err.println("Opening statement " + statementHandle);

            Connection connection = CONNECTION_MAP.get(connectionHandle);
            p = new NamedParameterStatement(connection, sql);
            STATEMENT_MAP.put(statementHandle, p);
            STATEMENT_COUNT_MAP.put(statementHandle, 1);
            
            Integer cnt = CONNECTION_COUNT_MAP.get(connectionHandle);
            int count = 0;
            if (cnt != null) {
                count = cnt;
            }
            count++;
            CONNECTION_COUNT_MAP.put(connectionHandle, count);

            if (IS_DEBUG) System.err.println("incConnectionCount count=" + count + " for connection " + connectionHandle);

        } else {
            Integer cnt = STATEMENT_COUNT_MAP.get(statementHandle);
            int count = 0;
            if (cnt != null) {
                count = cnt;
            }
            count++;
            STATEMENT_COUNT_MAP.put(statementHandle, count);

            if (IS_DEBUG) System.err.println("openStatement count=" + count + " for statement " + statementHandle);
        }

        return statementHandle;
    }

    private String openResultSet(AssocArray in) throws SQLException {

        boolean isFirst = true;
        StringBuilder sb = new StringBuilder();
        for (Object key : in.keySet()) {
            String s = key.toString();
            if (s.startsWith(":")) {
                if (!isFirst) {
                    sb.append(",");
                }
                isFirst = false;
                Object value = in.get(s);
                sb.append(s).append("=").append(value);
            }
        }
        String params = StringEscapeUtils.escapeJava(sb.toString());

        String statementHandle = openStatement(in, "sqlQuery");
        String resultSetHandle = statementHandle + "\0" + params;
        // if (IS_DEBUG) System.err.println("*** handle=" + resultSetHandle);

        if (RESULTSET_MAP.get(resultSetHandle) != null) {
            throw new IllegalAwkArgumentException("Query already open in openDatabaseQuery(), params=" + params);
        }

        NamedParameterStatement p = STATEMENT_MAP.get(statementHandle);
        for (Object key : in.keySet()) {
            String s = key.toString();
            if (s.startsWith(":")) {
                Object value = in.get(s);
                if (value instanceof Integer) {
                    p.setInt(s.substring(1), (Integer) value);
                } else if (value instanceof Double) {
                    p.setDouble(s.substring(1), (Double) value);
                } else if (value instanceof String) {
                    p.setString(s.substring(1), (String) value);
                } else if (value instanceof AwkNode) {
                    p.setString(s.substring(1), value.toString());
                } else {
                    throw new IllegalAwkArgumentException("Invalid value in openDatabaseQuery()");
                }
            }
        }

        if (IS_DEBUG) System.err.println("Opening resultSet " + resultSetHandle);

        ResultSet rs = p.executeQuery();
        RESULTSET_MAP.put(resultSetHandle, rs);

        return resultSetHandle;
    }

    private boolean closeResultSet(String resultSetHandle, ResultSet rs) throws SQLException {
        if (rs == null) {
            rs = RESULTSET_MAP.get(resultSetHandle);
        }
        if (rs != null) {
            if (IS_DEBUG) System.err.println("Closing resultSet " + resultSetHandle);

            rs.close();
            RESULTSET_MAP.remove(resultSetHandle);
            
            return true;
        }
        
        return false;
    }

    private void decStatementCount(String statementHandle) {
        Integer cnt = STATEMENT_COUNT_MAP.get(statementHandle);
        int count = 1;
        if (cnt != null) {
            count = cnt;
        }
        count--;
        STATEMENT_COUNT_MAP.put(statementHandle, count);

        if (IS_DEBUG) System.err.println("decStatementCount count=" + count + " for statement " + statementHandle);
    }

    private void closeStatement(String handle) throws SQLException {

        String[] handleFields = handle.split("\0");

        if ((handleFields.length < 5) || (handleFields.length > 6)) {
            throw new IllegalAwkArgumentException("Invalid handle in closeDatabaseStatement()");
        }

        if (handleFields.length == 6) {

            boolean closed = closeResultSet(handle, null);

            try {
                handle = handle.substring(0, handle.lastIndexOf("\0"));
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in closeDatabaseStatement()");
            }

            if (closed) {
                decStatementCount(handle);
            }
        }

        Integer cnt = STATEMENT_COUNT_MAP.get(handle);
        int count = 1;
        if (cnt != null) {
            count = cnt;
        }
        if (count <= 0) {

            if (IS_DEBUG) System.err.println("Closing statement " + handle);

            STATEMENT_COUNT_MAP.remove(handle);

            NamedParameterStatement p = STATEMENT_MAP.get(handle);
            if (p != null) {
                p.close();
                STATEMENT_MAP.remove(handle);
            }

            try {
                handle = handle.substring(0, handle.lastIndexOf("\0"));
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in closeDatabaseStatement()");
            }

            cnt = CONNECTION_COUNT_MAP.get(handle);
            count = 1;
            if (cnt != null) {
                count = cnt;
            }
            count--;
            CONNECTION_COUNT_MAP.put(handle, count);

            if (IS_DEBUG) System.err.println("decConnectionCount count=" + count + " for connection " + handle);

            closeConnection(handle);
        }
    }

    private Object getValue(ResultSet rs, int i, String ifColumnNull) throws SQLException {
        Object value = null;

        ResultSetMetaData meta = rs.getMetaData();
        switch (meta.getColumnType(i)) {
        case Types.BIT:
        case Types.INTEGER:
        case Types.SMALLINT:
        case Types.TINYINT:
            value = rs.getInt(i);
            break;

        case Types.DECIMAL:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.NUMERIC:
        case Types.REAL:
            value = rs.getDouble(i);
            break;

        case Types.TIMESTAMP:
            Date date = rs.getTimestamp(i);
            if (date != null) {
                if (myDateFormat == null) {
                    myDateFormat = new SimpleDateFormat(getVm().getDATEFMT().toString());
                }
                value = myDateFormat.format(date);
            }
            break;

        case Types.CLOB:
            Clob clob = rs.getClob(i);
            if (clob != null) {
                value = clob.getSubString(1, (int) clob.length());
            }
            break;

        case Types.BLOB:
            Blob blob = rs.getBlob(i);
            if (blob != null) {
                value = new String(blob.getBytes(1, (int) blob.length()));
            }
            break;

        case Types.VARBINARY:
        case Types.LONGVARBINARY:
            byte[] bytes = rs.getBytes(i);
            if (bytes != null) {
                value = new String(bytes);
            }
            break;

        default:
            value = rs.getString(i);
            break;
        }

        if (rs.wasNull() || (value == null)) {
            value = ifColumnNull;
        }

        return value;
    }

    @Override
    public Object invoke(String keyword, Object[] args) {
        if (keyword.equals("openDatabaseQuery")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Invalid input array in openDatabaseQuery()");
            }
            AssocArray in = (AssocArray) args[0];
            try {
                return openResultSet(in);
            } catch (SQLException e) {
                throw new AwkFunctionException(keyword, e);
            }
        } else if (keyword.equals("getDatabaseRow")) {
            if ((args.length < 1) || (args.length > 3)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in getDatabaseRow()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in getDatabaseRow()");
            }
            String handle = args[0].toString();

            ResultSet rs = RESULTSET_MAP.get(handle);
            if (rs == null) {
                throw new IllegalAwkArgumentException("Invalid handle in getDatabaseRow()");
            }

            String ifColumnNull = "";
            if (args.length >= 2) {
                if (args[1] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyColumn argument in getDatabaseRow()");
                }
                ifColumnNull = args[1].toString();
            }

            AssocArray outColumns = null;
            if (args.length == 3) {
                if (!(args[2] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid output array in getDatabaseRow()");
                }
                outColumns = (AssocArray) args[2];
                //outColumns.clear();
                outColumns.useMapType(AssocArray.MT_LINKED);
            }

            StringBuilder line = new StringBuilder();

            try {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                String ifs = null;

                if (rs.next()) {

                    for (int i = 1; i <= columnCount; i++) {

                        Object value = getValue(rs, i, ifColumnNull);

                        if (i > 1) {
                            if (ifs == null) {
                                ifs = getVm().getIFS().toString();
                                ifs = StringEscapeUtils.unescapeJava(ifs);
                            }
                            line.append(ifs);
                        }

                        if (value instanceof Double) {
                            line.append(String.format(getVm().getCONVFMT().toString(), value));
                        } else {
                            line.append(value);
                        }

                        if (outColumns == null) {
                            continue;
                        }

                        String label = meta.getColumnLabel(i);
                        outColumns.put(label, value);
                    }
                } else {

                    closeResultSet(handle, rs);

                    try {
                        handle = handle.substring(0, handle.lastIndexOf("\0"));
                    } catch (Exception e) {
                        throw new IllegalAwkArgumentException("Invalid handle in getDatabaseRow()");
                    }
                    decStatementCount(handle);

                    //return "";
                    return "\0";
                }
            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }

            myDateFormat = null;

            return line.toString();

        } else if (keyword.equals("queryDatabase")) {
            if ((args.length < 2) || (args.length > 3)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in queryDatabase()");
            }

            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Invalid input array in queryDatabase()");
            }
            AssocArray in = (AssocArray) args[0];

            if (!(args[1] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Invalid output array in queryDatabase()");
            }
            AssocArray outArrays = (AssocArray) args[1];
            //outArrays.clear();
            outArrays.useMapType(AssocArray.MT_LINKED);

            AssocArray outLines = null;
            if (args.length == 3) {
                if (!(args[2] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid output lines array in queryDatabase()");
                }
                outLines = (AssocArray) args[2];
                //outLines.clear();
                outLines.useMapType(AssocArray.MT_LINKED);
            }

            String ifColumnNull = "";
            Object t = Util.get(in, "emptyColumn");
            if (t != null) {
                ifColumnNull = t.toString();
            }

            String handle = "";

            ResultSet rs = null;
            try {
                handle = openStatement(in, "sqlQuery");
                NamedParameterStatement p = STATEMENT_MAP.get(handle);

                for (Object key : in.keySet()) {
                    String s = key.toString();
                    if (s.startsWith(":")) {
                        Object value = in.get(s);
                        if (value instanceof Integer) {
                            p.setInt(s.substring(1), (Integer) value);
                        } else if (value instanceof Double) {
                            p.setDouble(s.substring(1), (Double) value);
                        } else if (value instanceof String) {
                            p.setString(s.substring(1), (String) value);
                        } else if (value instanceof AwkNode) {
                            p.setString(s.substring(1), value.toString());
                        } else {
                            throw new IllegalAwkArgumentException("Invalid value in queryDatabase()");
                        }
                    }
                }

                rs = p.executeQuery();
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                String ifs = null;

                int rowCount = 1;
                while (rs.next()) {
                    AssocArray row = new AssocArray(false, getVm());
                    row.useMapType(AssocArray.MT_LINKED);
                    
                    outArrays.put(rowCount, row);

                    StringBuilder line = null;

                    for (int i = 1; i <= columnCount; i++) {

                        Object value = getValue(rs, i, ifColumnNull);

                        String label = meta.getColumnLabel(i);
                        row.put(label, value);

                        if (outLines == null) {
                            continue;
                        }

                        if (line == null) {
                            line = new StringBuilder();
                        } else {
                            if (ifs == null) {
                                ifs = getVm().getIFS().toString();
                                ifs = StringEscapeUtils.unescapeJava(ifs);
                            }
                            line.append(ifs);
                        }
                        if (value instanceof Double) {
                            line.append(String.format(getVm().getCONVFMT().toString(), value));
                        } else {
                            line.append(value);
                        }
                    }

                    if ((outLines != null) && (line != null)) {
                        outLines.put(rowCount, line.toString());
                    }

                    rowCount++;
                }
            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            } finally {
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (SQLException e) {
                    }
                }

                decStatementCount(handle);
            }

            myDateFormat = null;

            return handle;

        } else if (keyword.equals("updateDatabase")) {
            if ((args.length < 1) || (args.length > 2)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in updateDatabase()");
            }

            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Invalid input array in updateDatabase()");
            }
            AssocArray in = (AssocArray) args[0];

            AssocArray out = null;
            if (args.length == 2) {
                if (!(args[1] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid output array in updateDatabase()");
                }
                out = (AssocArray) args[1];
                out.clear();
            }

            String handle = "";

            try {
                handle = openStatement(in, "sql");
                NamedParameterStatement p = STATEMENT_MAP.get(handle);

                for (Object key : in.keySet()) {
                    String s = key.toString();
                    if (s.startsWith(":")) {
                        Object value = in.get(s);
                        if (value instanceof Integer) {
                            p.setInt(s.substring(1), (Integer) value);
                        } else if (value instanceof Double) {
                            p.setDouble(s.substring(1), (Double) value);
                        } else if (value instanceof String) {
                            p.setString(s.substring(1), (String) value);
                        } else if (value instanceof AwkNode) {
                            p.setString(s.substring(1), value.toString());
                        } else {
                            throw new IllegalAwkArgumentException("Invalid value in updateDatabase()");
                        }
                    }
                }

                Integer retVal = p.executeUpdate();
                if (out != null) {
                    out.put(1, retVal);
                }

                decStatementCount(handle);

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }

            return handle;

        } else if (keyword.equals("closeDatabaseStatement")) {
            checkNumArgs(args, 1, keyword);
            try {
                closeStatement(args[0].toString());
            } catch (SQLException e) {
                throw new AwkFunctionException(keyword, e);
            }
            return "";
        } else if (keyword.equals("loadArray")) {
            checkNumArgs(args, 2, keyword);

            if (!(args[0] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Invalid array in loadArray()");
            }
            AssocArray aa = (AssocArray) args[0];
            aa.clear();

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid file name in loadArray()");
            }
            String fileName = args[1].toString().trim();

            InputStream in = null;

            try {
                if (!(new File(fileName).exists())) {
                    in = getClass().getResourceAsStream(fileName);
                    if (in == null) {
                        in = getClass().getClassLoader().getResourceAsStream(fileName);
                    }
                    if (in == null) {
                        in = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
                    }
                    if (in == null) {
                        in = ClassLoader.getSystemClassLoader().getResourceAsStream(fileName);
                    }

                    if (in == null) {
                        throw new FileNotFoundException(fileName);
                    }
                } else {
                    in = new FileInputStream(fileName);
                }

                in = new BufferedInputStream(in);
                Properties props = new Properties();

                if (fileName.endsWith(".xml")) {
                    props.loadFromXML(in);
                } else {
                    props.load(in);
                }

                for (Object key : props.keySet()) {
                    aa.put(key, props.get(key));
                }

            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }

            return aa;

        } else {
            throw new NotImplementedError(keyword);
        }
    }

    public static void open(VariableManager vm, InputContext<DatabaseContext> context) throws IOException {
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

        String driverClass = context.myProps.getProperty("driverClass");
        if (driverClass == null) {
            throw new IllegalAwkArgumentException("driverClass not specified");
        }
        driverClass = driverClass.trim();
        String url = context.myProps.getProperty("databaseUrl");
        if (url == null) {
            throw new IllegalAwkArgumentException("databaseUrl not specified");
        }
        url = url.trim();
        String userName = context.myProps.getProperty("databaseUserName");
        if (userName == null) {
            throw new IllegalAwkArgumentException("databaseUserName not specified");
        }
        String password = context.myProps.getProperty("databasePassword");
        if (password == null) {
            throw new IllegalAwkArgumentException("databasePassword not specified");
        }
        String query = context.myProps.getProperty("sqlQuery");
        if (query == null) {
            throw new IllegalAwkArgumentException("sqlQuery not specified");
        }
        query = query.trim();

        context.myRow = -1;
        context.mySource = new DatabaseContext();

        try {

            Object instance = Class.forName(driverClass).newInstance();
            DriverManager.registerDriver((Driver) instance);

            Connection connection = DriverManager.getConnection(url, userName, password);

            NamedParameterStatement p = new NamedParameterStatement(connection, query);
            context.mySource.myStatement = p;

            for (Object key : context.myProps.keySet()) {
                String s = key.toString();
                if (s.startsWith(":")) {
                    p.setString(s.substring(1), context.myProps.getProperty(s));
                }
            }

            context.mySource.myResultSet = p.executeQuery();

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static String getLine(VariableManager vm, InputContext<DatabaseContext> context) throws IOException {
        if (context.mySource == null) {
            return null;
        }

        String ifs = vm.getIFS().toString();
        ifs = StringEscapeUtils.unescapeJava(ifs);

        StringBuilder line = new StringBuilder();

        String ifColumnNull = context.myProps.getProperty("emptyColumn", "");

        String getHeaders = context.myProps.getProperty("databaseHeaders", "false").trim();

        try {

            ResultSetMetaData meta = context.mySource.myResultSet.getMetaData();
            int columnCount = meta.getColumnCount();

            if ((context.myRow == -1) && (getHeaders.equals("true") || getHeaders.equals("1"))) {
                for (int i = 1; i <= columnCount; i++) {
                    String label = meta.getColumnLabel(i);
                    if (i > 1) {
                        line.append(ifs);
                    }
                    line.append(label);
                }
                context.myRow = 0;
            } else {
                context.myRow++;
                if (!context.mySource.myResultSet.next()) {
                    return null;
                }
                for (int i = 1; i <= columnCount; i++) {
                    String s = context.mySource.myResultSet.getString(i);
                    if (context.mySource.myResultSet.wasNull() || (s == null)) {
                        s = ifColumnNull;
                    }
                    if (i > 1) {
                        line.append(ifs);
                    }
                    line.append(s);
                }
            }

        } catch (Exception e) {
            throw new IOException(e);
        }

        return line.toString();
    }

    public static void close(InputContext<DatabaseContext> context) throws IOException {
        if (context.mySource == null) {
            return;
        }
        try {
            context.mySource.myResultSet.close();
            Connection c = context.mySource.myStatement.getConnection();
            context.mySource.myStatement.close();
            c.close();
            context.mySource = null;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
