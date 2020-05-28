package com.jagacy.totalAccess.ext;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jawk.NotImplementedError;
import org.jawk.ext.AbstractExtension;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;

import com.jagacy.totalAccess.jrt.InputContext;
import com.jagacy.totalAccess.util.Util;
import com.opencsv.CSVParser;
import com.opencsv.CSVReader;

public class CsvExtension extends AbstractExtension {

    private static final Map<String, CSVReader> READER_MAP = new Hashtable<String, CSVReader>();

    @Override
    public String[] extensionKeywords() {
        return new String[] { "openCsv", "getCsvLine", "parseCsvLine", "closeCsv", "escapeCsv", "unescapeCsv",
                "parseFixedLine" };
    }

    @Override
    public String getExtensionName() {
        return "CSV/Fixed column support";
    }

    @Override
    public Object invoke(String keyword, Object[] args) {
        if (keyword.equals("openCsv")) {
            if ((args.length < 1) || (args.length > 2)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in openCsv()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid file name in openCsv()");
            }
            String fileName = args[0].toString().trim();
            if (fileName.contentEquals("")) {
                throw new AwkFunctionException(keyword, "Invalid file name");
            }

            String handle = "csv" + "\0" + fileName + "\0" + Thread.currentThread().getId();

            if (READER_MAP.get(handle) != null) {
                throw new IllegalAwkArgumentException("File already open in openCsv(): " + fileName);
            }

            String delimiter = ",";
            if (args.length == 2) {
                if (args[1] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid delimiter in openCsv()");
                }
                delimiter = args[1].toString();
            }
            if (delimiter.length() != 1) {
                throw new IllegalAwkArgumentException("Delimiter must be 1 character in openCsv()=" + delimiter);
            }

            CSVReader reader;

            try {
                reader = new CSVReader(new BufferedReader(new FileReader(fileName)), delimiter.charAt(0));
            } catch (FileNotFoundException e) {
                throw new AwkFunctionException(keyword, e);
            }

            READER_MAP.put(handle, reader);

            return handle;

        } else if (keyword.equals("getCsvLine")) {
            if ((args.length < 1) || (args.length > 5)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in getCsvLine()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in getCsvLine()");
            }
            String handle = args[0].toString();

            CSVReader reader = READER_MAP.get(handle);
            if (reader == null) {
                throw new IllegalAwkArgumentException("Invalid handle in getCsvLine()");
            }

            String ifRowNull = "null";
            String ifColumnNull = "";
            boolean toNumber = false;

            if (args.length >= 2) {
                if (args[1] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyRow argument in getCsvLine()");
                }
                ifRowNull = args[1].toString();
            }

            if (args.length >= 3) {
                if (args[2] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyColumn argument in getCsvLine()");
                }
                ifColumnNull = args[2].toString();
            }

            AssocArray outFields = null;
            if (args.length >= 4) {
                if (!(args[3] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid out fields argument in getCsvLine()");
                }
                outFields = (AssocArray) args[3];
                //outFields.clear();
                outFields.useMapType(AssocArray.MT_LINKED);
            }

            if (args.length == 5) {
                if (args[4] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid toNumber argument in getCsvLine()");
                }
                toNumber = JRT.toAwkBoolean(args[4]);
            }

            String[] fields;
            try {
                fields = reader.readNext();
            } catch (IOException e) {
                throw new AwkFunctionException(keyword, e);
            }

            if (fields == null) {
                // return "";
                return "\0";
            }

            // For empty lines, return 1 entry in outFields.
            if ((fields.length == 0) || ((fields.length == 1) && fields[0].equals(""))) {
                if (outFields != null) {
                    outFields.put(1, "");
                }
                return ifRowNull;
            }

            String ifs = getVm().getIFS().toString();
            ifs = StringEscapeUtils.unescapeJava(ifs);

            boolean isFirst = true;
            StringBuilder line = new StringBuilder();
            int maxField = fields.length;
            for (int f = 0; f < maxField; f++) {
                Object field = fields[f];

                if ((field == null) || (field.equals(""))) {
                    field = ifColumnNull;
                }

                if (!isFirst) {
                    line.append(ifs);
                }
                isFirst = false;

                // field = StringEscapeUtils.unescapeCsv(field);

                line.append(field);

                if (outFields != null) {
                    if (toNumber) {
                        String s = field.toString().trim();
                        if (NumberUtils.isCreatable(s)) {
                            Number n = Double.parseDouble(s);
                            if (n.doubleValue() == n.intValue()) {
                                n = n.intValue();
                            }
                            field = n;
                        }
                    }

                    outFields.put(f + 1, field);
                }
            }

            return line.toString();
        } else if (keyword.equals("closeCsv")) {
            checkNumArgs(args, 1, keyword);

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in closeCsv()");
            }
            String handle = args[0].toString();

            CSVReader reader = READER_MAP.get(handle);

            if (reader == null) {
                throw new IllegalAwkArgumentException("Invalid handle in closeCsv()");
            }

            try {
                reader.close();
            } catch (IOException e) {
                throw new AwkFunctionException(keyword, e);
            }

            READER_MAP.remove(handle);

            return "";

        } else if (keyword.equals("parseCsvLine")) {
            if ((args.length < 1) || (args.length > 6)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in parseCsvLine()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid input argument in parseCsvLine()");
            }
            String input = args[0].toString();

            String delimiter = ",";
            String ifRowNull = "null";
            String ifColumnNull = "";
            boolean toNumber = false;

            if (args.length >= 2) {
                if (args[1] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid delimiter argument in parseCsvLine()");
                }
                delimiter = args[1].toString();
            }

            if (args.length >= 3) {
                if (args[2] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyRow argument in parseCsvLine()");
                }
                ifRowNull = args[2].toString();
            }

            if (args.length >= 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyColumn argument in parseCsvLine()");
                }
                ifColumnNull = args[3].toString();
            }

            AssocArray outFields = null;
            if (args.length >= 5) {
                if (!(args[4] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid out fields argument in parseCsvLine()");
                }
                outFields = (AssocArray) args[4];
                //outFields.clear();
                outFields.useMapType(AssocArray.MT_LINKED);
            }

            if (args.length == 6) {
                if (args[5] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid toNumber argument in parseCsvLine()");
                }
                toNumber = JRT.toAwkBoolean(args[5]);
            }

            if (delimiter.length() != 1) {
                throw new IllegalAwkArgumentException("Delimiter must be 1 character in parseCsvLine()=" + delimiter);
            }

            CSVParser parser = new CSVParser(delimiter.charAt(0));

            String[] fields;
            try {
                fields = parser.parseLine(input);
            } catch (IOException e) {
                throw new AwkFunctionException(keyword, e);
            }

            if (fields == null) {
                return "";
            }

            // For empty lines, return 1 entry in outFields.
            if ((fields.length == 0) || ((fields.length == 1) && fields[0].equals(""))) {
                if (outFields != null) {
                    outFields.put(1, "");
                }
                return ifRowNull;
            }

            String ifs = getVm().getIFS().toString();
            ifs = StringEscapeUtils.unescapeJava(ifs);

            boolean isFirst = true;
            StringBuilder line = new StringBuilder();
            int maxField = fields.length;
            for (int f = 0; f < maxField; f++) {
                Object field = fields[f];

                if ((field == null) || (field.equals(""))) {
                    field = ifColumnNull;
                }

                if (!isFirst) {
                    line.append(ifs);
                }
                isFirst = false;

                // field = StringEscapeUtils.unescapeCsv(field);

                line.append(field);

                if (outFields != null) {
                    if (toNumber) {
                        String s = field.toString().trim();
                        if (NumberUtils.isCreatable(s)) {
                            Number n = Double.parseDouble(s);
                            if (n.doubleValue() == n.intValue()) {
                                n = n.intValue();
                            }
                            field = n;
                        }
                    }

                    outFields.put(f + 1, field);
                }
            }

            return line.toString();
        } else if (keyword.equals("parseFixedLine")) {
            if ((args.length < 2) || (args.length > 4)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in parseFixedLine()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid inputLine argument in parseFixedLine()");
            }
            String input = args[0].toString();

            if (!(args[1] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Argument 2 is not an array in parseFixedLine()");
            }
            AssocArray widths = (AssocArray) args[1];

            AssocArray outFields = null;
            if (args.length >= 3) {
                if (!(args[2] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Argument 3 is not an array in parseFixedLine()");
                }
                outFields = (AssocArray) args[2];
                //outFields.clear();
                outFields.useMapType(AssocArray.MT_LINKED);
            }

            boolean toNumber = false;
            if (args.length == 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid toNumber argument in parseFixedLine()");
                }
                toNumber = JRT.toAwkBoolean(args[3]);
            }

            String ifs = getVm().getIFS().toString();
            ifs = StringEscapeUtils.unescapeJava(ifs);

            boolean isFirst = true;
            StringBuilder line = new StringBuilder();
            int maxField = widths.size();
            for (int i = 1; i <= maxField; i++) {
                Object w = Util.get(widths, i);
                if ((w == null) || (!(w instanceof Number) && !NumberUtils.isCreatable(w.toString().trim()))) {
                    throw new IllegalAwkArgumentException("Invalid width in parseFixedLine(), index:" + i);
                }
                int width = (int) JRT.toDouble(w);
                int len = input.length();
                if (len == 0) {
                    break;
                }
                if (width > len) {
                    width = len;
                }
                Object field = input.substring(0, width);
                input = input.substring(width);

                if (!isFirst) {
                    line.append(ifs);
                }
                isFirst = false;

                line.append(field);

                if (outFields != null) {

                    if (toNumber) {
                        String s = field.toString().trim();
                        if (NumberUtils.isCreatable(s)) {
                            Number n = Double.parseDouble(s);
                            if (n.doubleValue() == n.intValue()) {
                                n = n.intValue();
                            }
                            field = n;
                        }
                    }

                    outFields.put(i, field);
                }
            }

            return line.toString();
        } else if (keyword.equals("unescapeCsv")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String)) {
                throw new IllegalAwkArgumentException("Argument is not a string in unescapeCsv()");
            }
            return StringEscapeUtils.unescapeCsv(args[0].toString());
        } else if (keyword.equals("escapeCsv")) {
            checkNumArgs(args, 1, keyword);
            if (!(args[0] instanceof String)) {
                throw new IllegalAwkArgumentException("Argument is not a string in escapeCsv()");
            }
            return StringEscapeUtils.escapeCsv(args[0].toString());
        } else {
            throw new NotImplementedError(keyword);
        }

        // return null;
    }

    public static void open(String fileName, VariableManager vm, InputContext<CSVReader> context) throws IOException {
        // context.myProps.clear();
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

        String delimiter = context.myProps.getProperty("delimiter", ",");
        if (delimiter.length() != 1) {
            throw new IllegalAwkArgumentException("CSV delimiter must be 1 character=" + delimiter);
        }

        context.mySource = new CSVReader(new BufferedReader(new FileReader(fileName)), delimiter.charAt(0));

    }

    public static void open(InputStream in, VariableManager vm, InputContext<CSVReader> context) throws IOException {
        // context.myProps.clear();
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

        String delimiter = context.myProps.getProperty("delimiter", ",");
        if (delimiter.length() != 1) {
            throw new IllegalAwkArgumentException("CSV delimiter must be 1 character=" + delimiter);
        }

        context.mySource = new CSVReader(new InputStreamReader(in), delimiter.charAt(0));

    }

    public static String getLine(VariableManager vm, InputContext<CSVReader> context) throws IOException {
        if (context.mySource == null) {
            return null;
        }
        String[] fields = context.mySource.readNext();

        if (fields == null) {
            context.mySource.close();
            context.mySource = null;
            return null;
        }

        String ifRowNull = context.myProps.getProperty("emptyRow", "null");
        String ifColumnNull = context.myProps.getProperty("emptyColumn", "");

        if ((fields.length == 0) || ((fields.length == 1) && fields[0].equals(""))) {
            return ifRowNull;
        }

        String ifs = vm.getIFS().toString();
        ifs = StringEscapeUtils.unescapeJava(ifs);

        StringBuilder line = new StringBuilder();
        boolean isFirst = true;

        for (String field : fields) {

            if ((field == null) || (field.equals(""))) {
                field = ifColumnNull;
            }

            if (!isFirst) {
                line.append(ifs);
            }
            isFirst = false;

            // field = StringEscapeUtils.unescapeCsv(field);
            // Fix for unescapeCsv
            // if (field.matches("^\".*[\n\",]+.*\"$")) {
            // field = field.substring(1, field.length() - 1);
            // }

            line.append(field);
        }

        return line.toString();
    }

    public static void close(InputContext<CSVReader> context) throws IOException {
        if (context.mySource == null) {
            return;
        }
        context.mySource.close();
        context.mySource = null;
    }
}
