package com.jagacy.totalAccess.ext;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jawk.NotImplementedError;
import org.jawk.ext.AbstractExtension;
import org.jawk.jrt.AssocArray;
import org.jawk.jrt.IllegalAwkArgumentException;
import org.jawk.jrt.JRT;
import org.jawk.jrt.VariableManager;

import com.jagacy.totalAccess.TotalAccess;
import com.jagacy.totalAccess.jrt.AwkNode;
import com.jagacy.totalAccess.jrt.InputContext;

public class ExcelExtension extends AbstractExtension {

    private static final boolean IS_DEBUG = Boolean.getBoolean("awkDebug");

    // Handles:
    //String workbookHandle = "excel" + "\0" + Thread.currentThread().getId() + "\0" + fileName;
    //String sheetHandle = workbookHandle + "\0" + safeName;
    
    // workbookHandle
    private static final Map<String, Integer> SHEET_COUNT_MAP = new Hashtable<String, Integer>();

    // workbookHandle
    private static final Map<String, Workbook> WORKBOOK_MAP = new Hashtable<String, Workbook>();

    // sheetHandle
    private static final Map<String, Sheet> SHEET_MAP = new Hashtable<String, Sheet>();

    // workbookHandle
    private static final Map<String, Boolean> IS_READONLY_MAP = new Hashtable<String, Boolean>();

    // workbookHandle
    private static final Map<String, Map<String, Font>> FONT_MAP = new Hashtable<String, Map<String, Font>>();

    // workbookHandle
    private static final Map<String, Map<String, CellStyle>> STYLE_MAP = new Hashtable<String, Map<String, CellStyle>>();

    // sheetHandle
    private static final Map<String, Integer> ROW_MAP = new Hashtable<String, Integer>();

    private static final Map<String, IndexedColors> COLOR_MAP = new Hashtable<String, IndexedColors>();


    
    @Override
    public String[] extensionKeywords() {
        return new String[] { "openExcel", "printExcelValue", "printExcelRTF", "printExcelDate", "printExcelFormula",
                "printExcelLink", "printExcelBoolean", "getExcelRowCount", "getExcelColumnCount", "getExcelValue",
                "getExcelSheetName", "getExcelLine", "createExcelFont", "createExcelCellStyle", "closeExcel" };
    }

    @Override
    public String getExtensionName() {
        return "Excel support";
    }

    private void resetReadonly(String sheetHandle) {
        String workbookHandle = null;
        try {
            String[] s = sheetHandle.split("\0");
            workbookHandle = s[0] + "\0" + s[1] + "\0" + s[2];
        } catch (Exception e) {
            throw new IllegalAwkArgumentException("Invalid handle");
        }

        IS_READONLY_MAP.put(workbookHandle, false);
    }

    @Override
    public Object invoke(String keyword, Object[] args) {
        if (keyword.equals("openExcel")) {
            if ((args.length < 1) || (args.length > 2)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in openExcel()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid file name argument in openExcel()");
            }
            String fileName = args[0].toString().trim();
            if (fileName.equals("")) {
                throw new IllegalAwkArgumentException(keyword, "Invalid file name argument");
            }

            String sheetName = "Sheet1";
            if (args.length == 2) {
                if (args[1] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid sheet name argument in openExcel()");
                }
                sheetName = args[1].toString();
            }
            String safeName = WorkbookUtil.createSafeSheetName(sheetName);
            
            if (!safeName.equals(sheetName)) {
                System.err.println("WARNING: Using safe sheet name '" + safeName + "' in openExcel()");
            }
            
            String workbookHandle = "excel"+ "\0" + Thread.currentThread().getId() + "\0" + fileName;
            String sheetHandle = workbookHandle + "\0" + safeName;

            if (SHEET_MAP.get(sheetHandle) != null) {
                throw new IllegalAwkArgumentException("Sheet already open in openExcel(): " + safeName);
            }

            Workbook wb = WORKBOOK_MAP.get(workbookHandle);
            if (wb != null) {
                ;
            } else if (!fileName.startsWith(":stream:") && new File(fileName).exists()) {
                try {
                    if (IS_DEBUG) System.err.println("Opening workbook " + workbookHandle);
                    // wb = WorkbookFactory.create(new InputStream);
                    wb = WorkbookFactory.create(new File(fileName));
                } catch (Exception e) {
                    throw new AwkFunctionException(keyword, e);
                }
            } else if (fileName.endsWith(".xls")) {
                if (IS_DEBUG) System.err.println("Creating workbook " + workbookHandle);
                wb = new HSSFWorkbook();
            } else if (fileName.endsWith(".xlsx")) {
                if (IS_DEBUG) System.err.println("Creating workbook " + workbookHandle);
                wb = new XSSFWorkbook();
            } else {
                throw new IllegalAwkArgumentException("Invalid file name in openExcel(): " + fileName);
            }

            Sheet sheet = wb.getSheet(safeName);
            if (sheet == null) {
                if (IS_DEBUG) System.err.println("Creating sheet " + sheetHandle);
                sheet = wb.createSheet(safeName);
            }

            SHEET_MAP.put(sheetHandle, sheet);
            ROW_MAP.put(sheetHandle, 0);
            WORKBOOK_MAP.put(workbookHandle, wb);
            int count = 1;
            Integer cnt = SHEET_COUNT_MAP.get(workbookHandle);
            if (cnt != null) {
                count = cnt + 1;
            }
            SHEET_COUNT_MAP.put(workbookHandle, count);
            IS_READONLY_MAP.put(workbookHandle, true);

            return sheetHandle;

        } else if (keyword.equals("printExcelValue")) {
            if ((args.length < 4) || (args.length > 5)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in printExcelValue()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelValue()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelValue()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelValue()");
            }
            if (args[2] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelValue()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;
            int c = (int) JRT.toDouble(args[2]) - 1;
            
            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelValue():" + (r + 1));
            }
            if (c < 0) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelValue():" + (c + 1));
            }

            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }

            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }

            if (args[3] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value argument in printExcelValue()");
            }
            Object value = args[3];

            if (value instanceof String) {
                cell.setCellValue((String) value);
            } else if (value instanceof Integer) {
                cell.setCellValue((Integer) value);
            } else if (value instanceof Double) {
                cell.setCellValue((Double) value);
            } else if (value instanceof AwkNode) {
                cell.setCellValue(value.toString());
            } else {
                throw new IllegalAwkArgumentException("Invalid value in printExcelValue()");
            }

            if (args.length == 5) {
                if (!(args[4] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid settings in printExcelValue()");
                }

                AssocArray styles = (AssocArray) args[4];
                doSettings(sheetHandle, cell, styles);
            }

            resetReadonly(sheetHandle);

            return sheetHandle;

        } else if (keyword.equals("printExcelRTF")) {
            if ((args.length < 4) || (args.length > 5)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in printExcelRTF()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelRTF()");
            }
            String sheetHandle = args[0].toString();
            
            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelRTF()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelRTF()");
            }
            if (args[2] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelRTF()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;
            int c = (int) JRT.toDouble(args[2]) - 1;
            
            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelRTF():" + (r + 1));
            }
            if (c < 0) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelRTF():" + (c + 1));
            }

            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }

            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }

            if (args[3] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value argument in printExcelRTF()");
            }
            Object value = args[3];

            if (!(value instanceof String) && !(value instanceof AwkNode)) {
                throw new IllegalAwkArgumentException("Invalid RTF string in printExcelRTF()");
            }

            CreationHelper createHelper = sheet.getWorkbook().getCreationHelper();
            cell.setCellValue(createHelper.createRichTextString(value.toString()));

            if (args.length == 5) {
                if (!(args[4] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid settings in printExcelRTF()");
                }

                AssocArray styles = (AssocArray) args[4];
                doSettings(sheetHandle, cell, styles);
            }

            resetReadonly(sheetHandle);

            return sheetHandle;

        } else if (keyword.equals("printExcelDate")) {
            if ((args.length < 4) || (args.length > 6)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in printExcelDate()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelDate()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelDate()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelDate()");
            }
            if (args[2] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelDate()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;
            int c = (int) JRT.toDouble(args[2]) - 1;

            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelDate():" + (r + 1));
            }
            if (c < 0) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelDate():" + (c + 1));
            }

            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }

            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }

            Date date = null;

            if (args[3] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid date argument in printExcelDate()");
            }
            
            String format;
            if (args.length == 6) {
                if (args[5] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid date format argument in printExcelDate()");
                }
                format = args[5].toString();
            } else {
                format = getVm().getDATEFMT().toString();
            }
            
            try {
                final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(format);
                date = DATE_FORMAT.parse(args[3].toString());
            } catch (Exception e) {
                throw new AwkFunctionException(keyword, e);
            }

            cell.setCellValue(date);

            // CellStyle style = cell.getCellStyle();
            // if ((style == null) || (style.getDataFormat() == 0x00)){
            // style = sheet.getWorkbook().createCellStyle();
            // }
            String workbookHandle = null;
            try {
                String[] s = sheetHandle.split("\0");
                workbookHandle = s[0] + "\0" + s[1] + "\0" + s[2];
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelDate()");
            }
            CellStyle style = getStyle(workbookHandle, "_date", sheet, true);
            style.setDataFormat((short) 0x16);
            cell.setCellStyle(style);

            if (args.length >= 5) {
                if (!(args[4] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid settings in printExcelDate()");
                }

                AssocArray styles = (AssocArray) args[4];
                doSettings(sheetHandle, cell, styles);
            }

            resetReadonly(sheetHandle);

            return sheetHandle;

        } else if (keyword.equals("printExcelFormula")) {
            if ((args.length < 4) || (args.length > 5)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in printExcelFormula()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelFormula()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelFormula()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelFormula()");
            }
            if (args[2] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelFormula()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;
            int c = (int) JRT.toDouble(args[2]) - 1;

            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelFormula():" + (r + 1));
            }
            if (c < 0) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelFormula():" + (c + 1));
            }

            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }

            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }

            if (args[3] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid formula argument in printExcelFormula()");
            }
            String formula = args[3].toString();

            cell.setCellFormula(formula);

            if (args.length == 5) {
                if (!(args[4] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid settings in printExcelFormula()");
                }

                AssocArray styles = (AssocArray) args[4];
                doSettings(sheetHandle, cell, styles);
            }

            resetReadonly(sheetHandle);

            return sheetHandle;

        } else if (keyword.equals("printExcelLink")) {
            if ((args.length < 5) || (args.length > 6)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in printExcelLink()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelLink()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelLink()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelLink()");
            }
            if (args[2] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelLink()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;
            int c = (int) JRT.toDouble(args[2]) - 1;

            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelLink():" + (r + 1));
            }
            if (c < 0) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelLink():" + (c + 1));
            }

            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }

            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }

            if (args[3] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value argument in printExcelLink()");
            }
            if (args[4] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid link argument in printExcelLink()");
            }
            
            String value = toAwkString(args[3]);
            String link = args[4].toString();

            cell.setCellValue(value);

            Workbook wb = sheet.getWorkbook();
            CreationHelper createHelper = wb.getCreationHelper();

            Hyperlink hlink;
            if (link.startsWith("http://") || link.startsWith("ftp://")) {
                hlink = createHelper.createHyperlink(HyperlinkType.URL);
                // if (!link.endsWith("/")) {
                // link += "/";
                // }
            } else if (link.startsWith("mailto:")) {
                hlink = createHelper.createHyperlink(HyperlinkType.EMAIL);
            } else if (link.indexOf('!') != -1) {
                hlink = createHelper.createHyperlink(HyperlinkType.DOCUMENT);
            } else {
                hlink = createHelper.createHyperlink(HyperlinkType.FILE);
            }
            hlink.setAddress(link);
            cell.setHyperlink(hlink);

            // CellStyle hlink_style = cell.getCellStyle();
            // if ((hlink_style == null) || (hlink_style.getDataFormat() == 0)){
            // hlink_style = wb.createCellStyle();
            // }
            //
            // short fontSize = 10;
            // String fontName = "Arial";
            // if (wb instanceof XSSFWorkbook) {
            // fontSize = 11;
            // fontName = "Calibri";
            // }
            // Font hlink_font = wb.findFont(false, IndexedColors.BLUE.index,
            // fontSize, fontName,
            // false, false, Font.SS_NONE, Font.U_SINGLE);
            // if (hlink_font == null) {
            // hlink_font = wb.createFont();
            // }
            String workbookHandle = null;
            try {
                String[] s = sheetHandle.split("\0");
                workbookHandle = s[0] + "\0" + s[1] + "\0" + s[2];
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelLink()");
            }
            CellStyle hlink_style = getStyle(workbookHandle, "_hlink", sheet, true);
            Font hlink_font = getFont(workbookHandle, "_hlinkFont", sheet, true);
            hlink_font.setUnderline(Font.U_SINGLE);
            hlink_font.setColor(IndexedColors.BLUE.getIndex());
            // hlink_font.setFontName(fontName);
            // hlink_font.setFontHeightInPoints(fontSize);
            hlink_style.setFont(hlink_font);
            cell.setCellStyle(hlink_style);

            if (args.length == 6) {
                if (!(args[5] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid settings in printExcelLink()");
                }

                AssocArray styles = (AssocArray) args[5];
                doSettings(sheetHandle, cell, styles);
            }

            resetReadonly(sheetHandle);

            return sheetHandle;

        } else if (keyword.equals("printExcelBoolean")) {
            if ((args.length < 4) || (args.length > 5)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in printExcelBoolean()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelBoolean()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in printExcelBoolean()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelBoolean()");
            }
            if (args[2] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelBoolean()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;
            int c = (int) JRT.toDouble(args[2]) - 1;

            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in printExcelBoolean():" + (r + 1));
            }
            if (c < 0) {
                throw new IllegalAwkArgumentException("Invalid column argument in printExcelBoolean():" + (c + 1));
            }

            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }

            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }

            if (args[3] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid value argument in printExcelBoolean()");
            }
            boolean b = JRT.toAwkBoolean(args[3]);

            cell.setCellValue(b);

            if (args.length == 5) {
                if (!(args[4] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid settings in printExcelBoolean()");
                }

                AssocArray styles = (AssocArray) args[4];
                doSettings(sheetHandle, cell, styles);
            }

            resetReadonly(sheetHandle);

            return sheetHandle;

        } else if (keyword.equals("getExcelRowCount")) {
            checkNumArgs(args, 1, keyword);

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelRowCount()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelRowCount()");
            }

            return sheet.getLastRowNum(); // - sheet.getFirstRowNum();

        } else if (keyword.equals("getExcelColumnCount")) {
            checkNumArgs(args, 2, keyword);

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelColumnCount()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelColumnCount()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in getExcelColumnCount()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;

            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in getExcelColumnCount():" + (r + 1));
            }

            Row row = sheet.getRow(r);
            int count = 0;
            if (row != null) {
                count = row.getLastCellNum();
            }

            return count;

        } else if (keyword.equals("getExcelValue")) {
            if ((args.length < 3) || (args.length > 6)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in getExcelValue()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelValue()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelValue()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid row argument in getExcelValue()");
            }
            if (args[2] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid column argument in getExcelValue()");
            }
            
            int r = (int) JRT.toDouble(args[1]) - 1;
            int c = (int) JRT.toDouble(args[2]) - 1;

            if (r < 0) {
                throw new IllegalAwkArgumentException("Invalid row argument in getExcelValue():" + (r + 1));
            }
            if (c < 0) {
                throw new IllegalAwkArgumentException("Invalid column argument in getExcelValue():" + (c + 1));
            }

            Object ifRowNull = "null";
            Object ifColumnNull = "";
            boolean awkBoolean = false;

            if (args.length >= 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid awkBoolean argument in getExcelValue()");
                }
                awkBoolean = JRT.toAwkBoolean(args[3]);
            }

            if (args.length >= 5) {
                if (args[4] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyRow argument in getExcelValue()");
                }
                ifRowNull = args[4];
            }

            if (args.length >= 6) {
                if (args[5] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyColumn argument in getExcelValue()");
                }
                ifColumnNull = args[5];
            }

            Row row = sheet.getRow(r);
            if (row == null) {
                return ifRowNull;
            }

            Cell cell = row.getCell(c);
            if (cell == null) {
                return ifColumnNull;
            }

            CellType type = cell.getCellTypeEnum();

            if (type == CellType.STRING) {
                return cell.getRichStringCellValue().getString();
            } else if (type == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(getVm().getDATEFMT().toString());
                    return DATE_FORMAT.format(cell.getDateCellValue());
                } else {
                    Number nValue = cell.getNumericCellValue();
                    if (nValue.doubleValue() == nValue.intValue()) {
                        nValue = nValue.intValue();
                    }
                    return nValue;
                }
            } else if (type == CellType.BOOLEAN) {
                if (cell.getBooleanCellValue()) {
                    if (awkBoolean) {
                        return 1;
                    } else {
                        return "TRUE";
                    }
                } else {
                    if (awkBoolean) {
                        return 0;
                    } else {
                        return "FALSE";
                    }
                }
            } else if (type == CellType.FORMULA) {
                try {
                    Number nValue = cell.getNumericCellValue();
                    if (nValue.doubleValue() == nValue.intValue()) {
                        nValue = nValue.intValue();
                    }
                    return nValue;
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            } else if (type == CellType.BLANK) {
                return "";
            } else {
                return cell.getStringCellValue();
            }
        } else if (keyword.equals("getExcelSheetName")) {
            checkNumArgs(args, 1, keyword);

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelSheetName()");
            }
            String sheetHandle = args[0].toString();

            String sheetName = null;
            try {
                sheetName = sheetHandle.substring(sheetHandle.lastIndexOf('\0') + 1);
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelSheetName()");
            }

            return sheetName;
        } else if (keyword.equals("getExcelLine")) {
            if ((args.length < 1) || (args.length > 5)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in getExcelLine()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelLine()");
            }
            String sheetHandle = args[0].toString();

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in getExcelLine()");
            }

            Object ifRowNull = "null";
            Object ifColumnNull = "";
            boolean awkBoolean = false;

            if (args.length >= 2) {
                if (args[1] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid awkBoolean argument in getExcelLine()");
                }
                awkBoolean = JRT.toAwkBoolean(args[1]);
            }

            if (args.length >= 3) {
                if (args[2] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyRow argument in getExcelLine()");
                }
                ifRowNull = args[2];
            }

            if (args.length >= 4) {
                if (args[3] instanceof AssocArray) {
                    throw new IllegalAwkArgumentException("Invalid emptyColumn argument in getExcelLine()");
                }
                ifColumnNull = args[3];
            }

            AssocArray outFields = null;
            if (args.length == 5) {
                if (!(args[4] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid out fields argument in getExcelLine()");
                }
                outFields = (AssocArray) args[4];
                //outFields.clear();
                outFields.useMapType(AssocArray.MT_LINKED);
            }

            int r = ROW_MAP.get(sheetHandle);
            if (r > sheet.getLastRowNum()) {
                //return ifRowNull;
                //return EMPTY;
                return "\0";
            }

            Row row = sheet.getRow(r);
            r++;
            ROW_MAP.put(sheetHandle, r);
            if (row == null) {
                return ifRowNull;
            }

            String ifs = getVm().getIFS().toString();
            ifs = StringEscapeUtils.unescapeJava(ifs);

            SimpleDateFormat DATE_FORMAT = null;

            boolean isFirst = true;
            StringBuilder line = new StringBuilder();
            int maxCell = row.getLastCellNum();
            for (int c = 0; c < maxCell; c++) {

                Object cellValue = null;

                Cell cell = row.getCell(c);
                if (cell == null) {
                    cellValue = ifColumnNull;
                } else {
                    CellType type = cell.getCellTypeEnum();

                    if (type == CellType.STRING) {
                        cellValue = cell.getRichStringCellValue().getString();
                    } else if (type == CellType.NUMERIC) {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            if (DATE_FORMAT == null) {
                                DATE_FORMAT = new SimpleDateFormat(getVm().getDATEFMT().toString());
                            }
                            cellValue = DATE_FORMAT.format(cell.getDateCellValue());
                        } else {
                            Number nValue = cell.getNumericCellValue();
                            if (nValue.doubleValue() == nValue.intValue()) {
                                nValue = nValue.intValue();
                            }
                            cellValue = nValue;
                        }
                    } else if (type == CellType.BOOLEAN) {
                        if (cell.getBooleanCellValue()) {
                            if (awkBoolean) {
                                cellValue = 1;
                            } else {
                                cellValue = "TRUE";
                            }
                        } else {
                            if (awkBoolean) {
                                cellValue = 0;
                            } else {
                                cellValue = "FALSE";
                            }
                        }
                    } else if (type == CellType.FORMULA) {
                        try {
                            Number nValue = cell.getNumericCellValue();
                            if (nValue.doubleValue() == nValue.intValue()) {
                                nValue = nValue.intValue();
                            }
                            cellValue = nValue;
                        } catch (Exception e) {
                            cellValue = cell.getStringCellValue();
                        }
                    } else if (type == CellType.BLANK) {
                        cellValue = "";
                    } else {
                        cellValue = cell.getStringCellValue();
                    }
                }

                if (outFields != null) {
                    outFields.put(c + 1, cellValue);
                }

                if (!isFirst) {
                    line.append(ifs);
                }
                isFirst = false;

                if (cellValue instanceof Double) {
                    cellValue = String.format(getVm().getCONVFMT().toString(), cellValue);
                }

                line.append(cellValue);
            }

            return line.toString();

        } else if (keyword.equals("createExcelFont")) {
            checkNumArgs(args, 3, keyword);

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in createExcelFont()");
            }
            String sheetHandle = args[0].toString();
            String workbookHandle = null;
            try {
                String[] s = sheetHandle.split("\0");
                workbookHandle = s[0] + "\0" + s[1] + "\0" + s[2];
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in createExcelFont()");
            }

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in createExcelFont()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid font id argument in createExcelFont()");
            }
            
            String fontId = args[1].toString().trim();
            if (fontId.equals("")) {
                throw new IllegalAwkArgumentException("Invalid font id in createExcelFont()");
            }

            if (!(args[2] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Invalid styles in createExcelFont()");
            }

            AssocArray styles = (AssocArray) args[2];
            doFontStyles(workbookHandle, fontId, sheet, styles);

            return fontId;

        } else if (keyword.equals("createExcelCellStyle")) {
            checkNumArgs(args, 3, keyword);

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in createExcelCellStyle()");
            }
            String sheetHandle = args[0].toString();
            String workbookHandle = null;
            try {
                String[] s = sheetHandle.split("\0");
                workbookHandle = s[0] + "\0" + s[1] + "\0" + s[2];
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in createExcelCellStyle()");
            }

            Sheet sheet = SHEET_MAP.get(sheetHandle);
            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in createExcelCellStyle()");
            }

            if (args[1] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid style id argument in createExcelCellStyle()");
            }
            String styleId = args[1].toString().trim();
            if (styleId.equals("")) {
                throw new IllegalAwkArgumentException("Invalid style id in createExcelCellStyle()");
            }

            if (!(args[2] instanceof AssocArray)) {
                throw new IllegalAwkArgumentException("Invalid styles in createExcelCellStyle()");
            }

            AssocArray styles = (AssocArray) args[2];
            doStyles(workbookHandle, styleId, sheet, styles);

            return styleId;

        } else if (keyword.equals("closeExcel")) {
            if ((args.length < 1) || (args.length > 2)) {
                throw new IllegalAwkArgumentException("Invalid number of arguments in closeExcel()");
            }

            if (args[0] instanceof AssocArray) {
                throw new IllegalAwkArgumentException("Invalid handle in closeExcel()");
            }
            String sheetHandle = args[0].toString();

            String workbookHandle = null;
            String fileName = null;
            try {
                String[] s = sheetHandle.split("\0");
                workbookHandle = s[0] + "\0" + s[1] + "\0" + s[2];
                fileName = s[2];
            } catch (Exception e) {
                throw new IllegalAwkArgumentException("Invalid handle in closeExcel()");
            }

            Sheet sheet = SHEET_MAP.get(sheetHandle);

            if (sheet == null) {
                throw new IllegalAwkArgumentException("Invalid handle in closeExcel()");
            }

            int count = SHEET_COUNT_MAP.get(workbookHandle);
            count--;
            SHEET_COUNT_MAP.put(workbookHandle, count);

            Workbook wb = sheet.getWorkbook();
            if (args.length == 2) {
                if (!(args[1] instanceof AssocArray)) {
                    throw new IllegalAwkArgumentException("Invalid settings in closeExcel()");
                }

                AssocArray settings = (AssocArray) args[1];
                for (Object setting : settings.keySet()) {
                    Object value = settings.get(setting);
                    if (setting.equals("selectedSheet") && JRT.toAwkBoolean(value)) {
                        wb.setSelectedTab(wb.getSheetIndex(sheet));
                        resetReadonly(sheetHandle);
                    } else if (setting.equals("activeSheet") && JRT.toAwkBoolean(value)) {
                        wb.setActiveSheet(wb.getSheetIndex(sheet));
                        resetReadonly(sheetHandle);
                    } else if (setting.equals("hiddenSheet")) {
                        wb.setSheetHidden(wb.getSheetIndex(sheet), JRT.toAwkBoolean(value));
                        resetReadonly(sheetHandle);
                    } else if (JRT.toAwkBoolean(value)
                            && setting.toString().startsWith("autoSizeColumn" + getVm().getSUBSEP())) {
                        String[] splits = setting.toString().split(getVm().getSUBSEP().toString());
                        if (splits.length != 2) {
                            throw new IllegalAwkArgumentException("Invalid autoSizeColumn setting subscript");
                        }
                        int column = (int) JRT.toDouble(splits[1]) - 1;
                        if (column < 0) {
                            throw new IllegalAwkArgumentException("Invalid autoSizeColumn column in closeExcel():" +
                                    (column + 1));
                        }
                        sheet.autoSizeColumn(column);
                        resetReadonly(sheetHandle);
                    } else if (setting.toString().startsWith("columnWidth" + getVm().getSUBSEP())) {
                        String[] splits = setting.toString().split(getVm().getSUBSEP().toString());
                        if (splits.length != 2) {
                            throw new IllegalAwkArgumentException("Invalid columnWidth setting subscript");
                        }
                        int column = (int) JRT.toDouble(splits[1]) - 1;
                        if (column < 0) {
                            throw new IllegalAwkArgumentException("Invalid columnWidth column in closeExcel():" +
                                    (column + 1));
                        }
                        sheet.setColumnWidth(column, (int) JRT.toDouble(value));
                        resetReadonly(sheetHandle);
                    } else {
                        throw new IllegalAwkArgumentException("Unknown setting=" + setting);
                    }
                }
            }

            if (IS_DEBUG) System.err.println("Closing sheet " + sheetHandle);
            SHEET_MAP.remove(sheetHandle);
            ROW_MAP.remove(sheetHandle);

            if (count > 0) {
                return "";
            }

            boolean isReadOnly = IS_READONLY_MAP.get(workbookHandle);

            SHEET_COUNT_MAP.remove(workbookHandle);
            IS_READONLY_MAP.remove(workbookHandle);
            WORKBOOK_MAP.remove(workbookHandle);
            FONT_MAP.remove(workbookHandle);
            STYLE_MAP.remove(workbookHandle);

            if (IS_DEBUG) System.err.println("Closing workbook " + workbookHandle);
            
            if (isReadOnly) {
                try {
                    sheet.getWorkbook().close();
                } catch (IOException e) {
                    throw new AwkFunctionException(keyword, e);
                }

                return "";
            }

            OutputStream fileOut = null;
            try {
                if (fileName.startsWith(":stream:")) {
                    fileOut = TotalAccess.getOutputStream();
                    if (fileOut == null) {
                        throw new AwkFunctionException(keyword, "Invalid output :stream:");
                    }
                    fileOut = new BufferedOutputStream(fileOut);
                } else {
                    fileOut = new BufferedOutputStream(new FileOutputStream(fileName));
                }
                sheet.getWorkbook().write(fileOut);
                sheet.getWorkbook().close();
            } catch (IOException e) {
                // System.out.println(e + "");

                // A workaround
                try {
                    final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
                    sheet.getWorkbook().write(bytesOut);
                    sheet.getWorkbook().close();

                    final byte[] byteArray = bytesOut.toByteArray();
                    bytesOut.close();

                    if (fileName.startsWith(":stream:")) {
                        fileOut = TotalAccess.getOutputStream();
                        if (fileOut == null) {
                            throw new AwkFunctionException(keyword, "Invalid output :stream:");
                        }
                        fileOut = new BufferedOutputStream(fileOut);
                    } else {
                        fileOut = new BufferedOutputStream(new FileOutputStream(fileName));
                    }
                    fileOut.write(byteArray);
                } catch (IOException e1) {
                    throw new AwkFunctionException(keyword, e1);
                } finally {
                    if (fileOut != null) {
                        try {
                            fileOut.close();
                            fileOut = null;
                        } catch (IOException e1) {
                            throw new AwkFunctionException(keyword, e1);
                        }
                    }
                }
            } finally {
                if (fileOut != null) {
                    try {
                        fileOut.close();
                        fileOut = null;
                    } catch (IOException e1) {
                        throw new AwkFunctionException(keyword, e1);
                    }
                }
            }

            return "";
        } else {
            throw new NotImplementedError(keyword);
        }
        // return null;
    }

    private void doFontStyles(String workbookHandle, String fontId, Sheet sheet, AssocArray styles) {

        Font font = createFont(workbookHandle, fontId, sheet);

        for (Object style : styles.keySet()) {
            Object value = styles.get(style);
            if (style.equals("fontColor")) {
                font.setColor(getColor(value.toString()));
            } else if (style.equals("bold")) {
                font.setBold(JRT.toAwkBoolean(value));
            } else if (style.equals("italic")) {
                font.setItalic(JRT.toAwkBoolean(value));
            } else if (style.equals("strikeout")) {
                font.setStrikeout(JRT.toAwkBoolean(value));
            } else if (style.equals("underline")) {
                String v = value.toString();
                byte type;
                if (v.equals("none")) {
                    type = Font.U_NONE;
                } else if (v.equals("single")) {
                    type = Font.U_SINGLE;
                } else if (v.equals("double")) {
                    type = Font.U_DOUBLE;
                } else if (v.equals("singleAccounting")) {
                    type = Font.U_SINGLE_ACCOUNTING;
                } else if (v.equals("doubleAccounting")) {
                    type = Font.U_DOUBLE_ACCOUNTING;
                } else {
                    throw new IllegalAwkArgumentException("Unknown underline type=" + v);
                }
                font.setUnderline(type);
            } else if (style.equals("fontName")) {
                font.setFontName(value.toString());
            } else if (style.equals("fontPointSize")) {
                font.setFontHeightInPoints((short) JRT.toDouble(value));
            } else {
                throw new IllegalAwkArgumentException("Unknown font style=" + style);
            }
        }
    }

    private void doStyles(String workbookHandle, String styleId, Sheet sheet, AssocArray styles) {

        CellStyle cellStyle = createStyle(workbookHandle, styleId, sheet);

        for (Object style : styles.keySet()) {
            Object value = styles.get(style);
            if (style.equals("useFont")) {
                cellStyle.setFont(getFont(workbookHandle, value.toString(), sheet, false));
            } else if (style.equals("halign")) {
                HorizontalAlignment halign;
                if (value.equals("center")) {
                    halign = HorizontalAlignment.CENTER;
                } else if (value.equals("left")) {
                    halign = HorizontalAlignment.LEFT;
                } else if (value.equals("right")) {
                    halign = HorizontalAlignment.RIGHT;
                } else if (value.equals("justify")) {
                    halign = HorizontalAlignment.JUSTIFY;
                } else if (value.equals("general")) {
                    halign = HorizontalAlignment.GENERAL;
                } else {
                    throw new IllegalAwkArgumentException("Unknown halign=" + value);
                }

                cellStyle.setAlignment(halign);
            } else if (style.equals("valign")) {
                VerticalAlignment valign;
                if (value.equals("center")) {
                    valign = VerticalAlignment.CENTER;
                } else if (value.equals("bottom")) {
                    valign = VerticalAlignment.BOTTOM;
                } else if (value.equals("top")) {
                    valign = VerticalAlignment.TOP;
                } else if (value.equals("justify")) {
                    valign = VerticalAlignment.JUSTIFY;
                } else if (value.equals("distributed")) {
                    valign = VerticalAlignment.DISTRIBUTED;
                } else {
                    throw new IllegalAwkArgumentException("Unknown valign=" + value);
                }

                cellStyle.setVerticalAlignment(valign);
            } else if (style.equals("wrapText")) {
                cellStyle.setWrapText(JRT.toAwkBoolean(value));
            } else if (style.equals("dataFormat")) {
                if (value instanceof Number) {
                    cellStyle.setDataFormat((short) JRT.toDouble(value));
                } else {
                    CreationHelper createHelper = sheet.getWorkbook().getCreationHelper();
                    cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(value.toString()));
                }
                // WARNING: Set foreground color before background color.
            } else if (style.equals("_fillForegroundColor")) {
                cellStyle.setFillForegroundColor(getColor(value.toString()));
            } else if (style.equals("fillBackgroundColor")) {
                cellStyle.setFillBackgroundColor(getColor(value.toString()));
            } else if (style.equals("fillPattern")) {
                cellStyle.setFillPattern(getFillPattern(value.toString()));
            } else if (style.equals("borderBottom")) {
                cellStyle.setBorderBottom(getBorderStyle(value.toString()));
            } else if (style.equals("borderLeft")) {
                cellStyle.setBorderLeft(getBorderStyle(value.toString()));
            } else if (style.equals("borderRight")) {
                cellStyle.setBorderRight(getBorderStyle(value.toString()));
            } else if (style.equals("borderTop")) {
                cellStyle.setBorderTop(getBorderStyle(value.toString()));
            } else if (style.equals("borderBottomColor")) {
                cellStyle.setBottomBorderColor(getColor(value.toString()));
            } else if (style.equals("borderLeftColor")) {
                cellStyle.setLeftBorderColor(getColor(value.toString()));
            } else if (style.equals("borderRightColor")) {
                cellStyle.setRightBorderColor(getColor(value.toString()));
            } else if (style.equals("borderTopColor")) {
                cellStyle.setTopBorderColor(getColor(value.toString()));
            } else if (style.equals("shrinkToFit")) {
                cellStyle.setShrinkToFit(JRT.toAwkBoolean(value));
            } else if (style.equals("quotePrefix")) {
                cellStyle.setQuotePrefixed(JRT.toAwkBoolean(value));
            } else if (style.equals("locked")) {
                cellStyle.setLocked(JRT.toAwkBoolean(value));
            } else if (style.equals("hidden")) {
                cellStyle.setHidden(JRT.toAwkBoolean(value));
            } else if (style.equals("rotation")) {
                cellStyle.setRotation((short) JRT.toDouble(value));
            } else if (style.equals("indentation")) {
                cellStyle.setIndention((short) JRT.toDouble(value));
            } else {
                throw new IllegalAwkArgumentException("Unknown style=" + style);
            }

        }
    }

    private void doSettings(String sheetHandle, Cell cell, AssocArray settings) {

        for (Object style : settings.keySet()) {
            Object value = settings.get(style);
            if (style.equals("useCellStyle")) {
                String workbookHandle = null;
                try {
                    String[] s = sheetHandle.split("\0");
                    workbookHandle = s[0] + "\0" + s[1] + "\0" + s[2];
                } catch (Exception e) {
                    throw new IllegalAwkArgumentException("Invalid handle");
                }
                cell.setCellStyle(getStyle(workbookHandle, value.toString(), null, false));
            } else if (style.equals("activeCell") && JRT.toAwkBoolean(value)) {
                cell.getSheet().setActiveCell(cell.getAddress());
            } else {
                throw new IllegalAwkArgumentException("Unknown setting=" + style);
            }
        }
    }

    private CellStyle createStyle(String workbookHandle, String id, Sheet sheet) {

        Map<String, CellStyle> styleMap = STYLE_MAP.get(workbookHandle);
        if (styleMap == null) {
            styleMap = new Hashtable<String, CellStyle>();
            STYLE_MAP.put(workbookHandle, styleMap);
        }

        CellStyle style = styleMap.get(id);
        if (style != null) {
            throw new IllegalAwkArgumentException("Style id " + id + " already exists");
        }

        style = sheet.getWorkbook().createCellStyle();
        styleMap.put(id, style);

        return style;

    }

    private CellStyle getStyle(String workbookHandle, String id, Sheet sheet, boolean isCreate) {

        Map<String, CellStyle> styleMap = STYLE_MAP.get(workbookHandle);
        if (styleMap == null) {
            if (isCreate) {
                styleMap = new Hashtable<String, CellStyle>();
                STYLE_MAP.put(workbookHandle, styleMap);
            } else {
                throw new IllegalAwkArgumentException("Style id " + id + " does not exist");
            }
        }

        CellStyle style = styleMap.get(id);
        if (style == null) {
            if (isCreate) {
                style = sheet.getWorkbook().createCellStyle();
                styleMap.put(id, style);
            } else {
                throw new IllegalAwkArgumentException("Style id " + id + " does not exist");
            }
        }

        return style;

    }

    private Font createFont(String workbookHandle, String id, Sheet sheet) {

        Map<String, Font> fontMap = FONT_MAP.get(workbookHandle);
        if (fontMap == null) {
            fontMap = new Hashtable<String, Font>();
            FONT_MAP.put(workbookHandle, fontMap);
        }

        Font font = fontMap.get(id);
        if (font != null) {
            throw new IllegalAwkArgumentException("Font id " + id + " already exists");
        }

        font = sheet.getWorkbook().createFont();
        fontMap.put(id, font);

        return font;

    }

    private Font getFont(String workbookHandle, String id, Sheet sheet, boolean isCreate) {

        Map<String, Font> fontMap = FONT_MAP.get(workbookHandle);
        if (fontMap == null) {
            if (isCreate) {
                fontMap = new Hashtable<String, Font>();
                FONT_MAP.put(workbookHandle, fontMap);
            } else {
                throw new IllegalAwkArgumentException("Font id " + id + " does not exist");
            }
        }

        Font font = fontMap.get(id);
        if (font == null) {
            if (isCreate) {
                font = sheet.getWorkbook().createFont();
                fontMap.put(id, font);
            } else {
                throw new IllegalAwkArgumentException("Font id " + id + " does not exist");
            }
        }

        return font;

    }

    private short getColor(String colorType) {
        IndexedColors color = COLOR_MAP.get(colorType);

        // TODO
        // if (color == null) {
        // String[] rgbs = colorType.split(",");
        // if (rgbs.length == 3) {
        //
        // }
        // }

        if (color == null) {
            throw new IllegalAwkArgumentException("Unknown color=" + colorType);
        }

        return color.index;
    }

    private FillPatternType getFillPattern(String fill) {
        if (fill.equals("altBars")) {
            return FillPatternType.ALT_BARS;
        } else if (fill.equals("bigSpots")) {
            return FillPatternType.BIG_SPOTS;
        } else if (fill.equals("bricks")) {
            return FillPatternType.BRICKS;
        } else if (fill.equals("diamonds")) {
            return FillPatternType.DIAMONDS;
        } else if (fill.equals("fineDots")) {
            return FillPatternType.FINE_DOTS;
        } else if (fill.equals("leastDots")) {
            return FillPatternType.LEAST_DOTS;
        } else if (fill.equals("lessDots")) {
            return FillPatternType.LESS_DOTS;
        } else if (fill.equals("noFill")) {
            return FillPatternType.NO_FILL;
        } else if (fill.equals("solidForeground")) {
            return FillPatternType.SOLID_FOREGROUND;
        } else if (fill.equals("sparseDots")) {
            return FillPatternType.SPARSE_DOTS;
        } else if (fill.equals("thickBackwardDiag")) {
            return FillPatternType.THICK_BACKWARD_DIAG;
        } else if (fill.equals("thickForwardDiag")) {
            return FillPatternType.THICK_FORWARD_DIAG;
        } else if (fill.equals("thinHorzBands")) {
            return FillPatternType.THIN_HORZ_BANDS;
        } else if (fill.equals("thinVertBands")) {
            return FillPatternType.THIN_VERT_BANDS;
        } else {
            throw new IllegalAwkArgumentException("Unknown fill pattern=" + fill);
        }
    }

    private BorderStyle getBorderStyle(String borderStyle) {
        if (borderStyle.equals("dashDot")) {
            return BorderStyle.DASH_DOT;
        } else if (borderStyle.equals("dashDotDot")) {
            return BorderStyle.DASH_DOT_DOT;
        } else if (borderStyle.equals("dashed")) {
            return BorderStyle.DASHED;
        } else if (borderStyle.equals("dotted")) {
            return BorderStyle.DOTTED;
        } else if (borderStyle.equals("double")) {
            return BorderStyle.DOUBLE;
        } else if (borderStyle.equals("hair")) {
            return BorderStyle.HAIR;
        } else if (borderStyle.equals("medium")) {
            return BorderStyle.MEDIUM;
        } else if (borderStyle.equals("mediumDashDot")) {
            return BorderStyle.MEDIUM_DASH_DOT;
        } else if (borderStyle.equals("mediumDashed")) {
            return BorderStyle.MEDIUM_DASHED;
        } else if (borderStyle.equals("none")) {
            return BorderStyle.NONE;
        } else if (borderStyle.equals("slantedDashDot")) {
            return BorderStyle.SLANTED_DASH_DOT;
        } else if (borderStyle.equals("thick")) {
            return BorderStyle.THICK;
        } else if (borderStyle.equals("thin")) {
            return BorderStyle.THIN;
        } else {
            throw new IllegalAwkArgumentException("Unknown border style=" + borderStyle);
        }
    }

    public static void open(String fileName, VariableManager vm, InputContext<Sheet> context) throws IOException {

        //context.myProps.clear();
        context.myRow = 0;
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

        Workbook wb;
        try {
            wb = WorkbookFactory.create(new File(fileName));
        } catch (Exception e) {
            throw new IOException(e);
        }

        String sheetName = context.myProps.getProperty("sheet", "Sheet1");
        context.mySource = wb.getSheet(sheetName);
        if (context.mySource == null) {
            wb.close();
            throw new IllegalAwkArgumentException("Invalid sheet=" + sheetName);
        }
    }

    public static void open(InputStream in, VariableManager vm, InputContext<Sheet> context) throws IOException {

        //context.myProps.clear();
        context.myRow = 0;
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

        Workbook wb = null;
        try {
            wb = WorkbookFactory.create(in);
        } catch (Exception e) {
            throw new IOException(e);
        }

        String sheetName = context.myProps.getProperty("sheet", "Sheet1");

        context.mySource = wb.getSheet(sheetName);
        if (context.mySource == null) {
            wb.close();
            throw new IllegalAwkArgumentException("Invalid sheet=" + sheetName);
        }
    }

    public static String getLine(VariableManager vm, InputContext<Sheet> context) {
        if (context.mySource == null) {
            return null;
        }

        if (context.myRow > context.mySource.getLastRowNum()) {
            return null;
        }

        String ifRowNull = context.myProps.getProperty("emptyRow", "null");
        String ifColumnNull = context.myProps.getProperty("emptyColumn", "");
        String awkBooleanProp = context.myProps.getProperty("awkBoolean", "false");
        awkBooleanProp = awkBooleanProp.trim();
        boolean awkBoolean = (awkBooleanProp.equals("true") || awkBooleanProp.equals("1"));

        Row row = context.mySource.getRow(context.myRow);
        context.myRow++;
        if (row == null) {
            return ifRowNull;
        }

        String ifs = vm.getIFS().toString();
        ifs = StringEscapeUtils.unescapeJava(ifs);

        SimpleDateFormat DATE_FORMAT = null;

        boolean isFirst = true;
        StringBuilder line = new StringBuilder();
        int maxCell = row.getLastCellNum();
        for (int c = 0; c < maxCell; c++) {

            Object cellValue = null;

            Cell cell = row.getCell(c);
            if (cell == null) {
                cellValue = ifColumnNull;
            } else {
                CellType type = cell.getCellTypeEnum();

                if (type == CellType.STRING) {
                    cellValue = cell.getRichStringCellValue().getString();
                } else if (type == CellType.NUMERIC) {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        if (DATE_FORMAT == null) {
                            DATE_FORMAT = new SimpleDateFormat(vm.getDATEFMT().toString());
                        }
                        cellValue = DATE_FORMAT.format(cell.getDateCellValue());
                    } else {
                        Number nValue = cell.getNumericCellValue();
                        if (nValue.doubleValue() == nValue.intValue()) {
                            nValue = nValue.intValue();
                        }
                        cellValue = nValue;
                    }
                } else if (type == CellType.BOOLEAN) {
                    if (cell.getBooleanCellValue()) {
                        if (awkBoolean) {
                            cellValue = "1";
                        } else {
                            cellValue = "TRUE";
                        }
                    } else {
                        if (awkBoolean) {
                            cellValue = "0";
                        } else {
                            cellValue = "FALSE";
                        }
                    }
                } else if (type == CellType.FORMULA) {
                    try {
                        Number nValue = cell.getNumericCellValue();
                        if (nValue.doubleValue() == nValue.intValue()) {
                            nValue = nValue.intValue();
                        }
                        cellValue = nValue;
                    } catch (Exception e) {
                        cellValue = cell.getStringCellValue();
                    }
                } else if (type == CellType.BLANK) {
                    cellValue = "";
                } else {
                    cellValue = cell.getStringCellValue();
                }
            }

            if (!isFirst) {
                line.append(ifs);
            }
            isFirst = false;

            if (cellValue instanceof Double) {
                cellValue = String.format(vm.getCONVFMT().toString(), cellValue);
            }

            line.append(cellValue);
        }

        return line.toString();
    }

    public static void close(InputContext<Sheet> context) throws IOException {
        if (context.mySource == null) {
            return;
        }

        try {
            context.mySource.getWorkbook().close();
        } finally {
            context.mySource = null;
        }
    }

    static {
        COLOR_MAP.put("aqua", IndexedColors.AQUA);
        COLOR_MAP.put("automatic", IndexedColors.AUTOMATIC);
        COLOR_MAP.put("black", IndexedColors.BLACK);
        COLOR_MAP.put("black1", IndexedColors.BLACK1);
        COLOR_MAP.put("blue", IndexedColors.BLUE);
        COLOR_MAP.put("blueGrey", IndexedColors.BLUE_GREY);
        COLOR_MAP.put("blue1", IndexedColors.BLUE1);
        COLOR_MAP.put("brightGreen", IndexedColors.BRIGHT_GREEN);
        COLOR_MAP.put("brightGreen1", IndexedColors.BRIGHT_GREEN1);
        COLOR_MAP.put("brown", IndexedColors.BROWN);
        COLOR_MAP.put("coral", IndexedColors.CORAL);
        COLOR_MAP.put("cornflowerBlue", IndexedColors.CORNFLOWER_BLUE);
        COLOR_MAP.put("darkBlue", IndexedColors.DARK_BLUE);
        COLOR_MAP.put("darkGreen", IndexedColors.DARK_GREEN);
        COLOR_MAP.put("darkRed", IndexedColors.DARK_RED);
        COLOR_MAP.put("darkTeal", IndexedColors.DARK_TEAL);
        COLOR_MAP.put("darkYellow", IndexedColors.DARK_YELLOW);
        COLOR_MAP.put("gold", IndexedColors.GOLD);
        COLOR_MAP.put("green", IndexedColors.GREEN);
        COLOR_MAP.put("grey25%", IndexedColors.GREY_25_PERCENT);
        COLOR_MAP.put("grey40%", IndexedColors.GREY_40_PERCENT);
        COLOR_MAP.put("grey50%", IndexedColors.GREY_50_PERCENT);
        COLOR_MAP.put("grey80%", IndexedColors.GREY_80_PERCENT);
        COLOR_MAP.put("indigo", IndexedColors.INDIGO);
        COLOR_MAP.put("lavender", IndexedColors.LAVENDER);
        COLOR_MAP.put("lemonChiffon", IndexedColors.LEMON_CHIFFON);
        COLOR_MAP.put("lightBlue", IndexedColors.LIGHT_BLUE);
        COLOR_MAP.put("lightCornflowerBlue", IndexedColors.LIGHT_CORNFLOWER_BLUE);
        COLOR_MAP.put("lightGreen", IndexedColors.LIGHT_GREEN);
        COLOR_MAP.put("lightOrange", IndexedColors.LIGHT_ORANGE);
        COLOR_MAP.put("lightTurquoise", IndexedColors.LIGHT_TURQUOISE);
        COLOR_MAP.put("lightTurquoise1", IndexedColors.LIGHT_TURQUOISE1);
        COLOR_MAP.put("lightYellow", IndexedColors.LIGHT_YELLOW);
        COLOR_MAP.put("lime", IndexedColors.LIME);
        COLOR_MAP.put("maroon", IndexedColors.MAROON);
        COLOR_MAP.put("oliveGreen", IndexedColors.OLIVE_GREEN);
        COLOR_MAP.put("orange", IndexedColors.ORANGE);
        COLOR_MAP.put("orchid", IndexedColors.ORCHID);
        COLOR_MAP.put("paleBlue", IndexedColors.PALE_BLUE);
        COLOR_MAP.put("pink", IndexedColors.PINK);
        COLOR_MAP.put("pink1", IndexedColors.PINK1);
        COLOR_MAP.put("plum", IndexedColors.PLUM);
        COLOR_MAP.put("red", IndexedColors.RED);
        COLOR_MAP.put("red1", IndexedColors.RED1);
        COLOR_MAP.put("rose", IndexedColors.ROSE);
        COLOR_MAP.put("royalBlue", IndexedColors.ROYAL_BLUE);
        COLOR_MAP.put("seaGreen", IndexedColors.SEA_GREEN);
        COLOR_MAP.put("skyBlue", IndexedColors.SKY_BLUE);
        COLOR_MAP.put("tan", IndexedColors.TAN);
        COLOR_MAP.put("turquoise", IndexedColors.TURQUOISE);
        COLOR_MAP.put("turquoise1", IndexedColors.TURQUOISE1);
        COLOR_MAP.put("violet", IndexedColors.VIOLET);
        COLOR_MAP.put("white", IndexedColors.WHITE);
        COLOR_MAP.put("white1", IndexedColors.WHITE1);
        COLOR_MAP.put("yellow", IndexedColors.YELLOW);
        COLOR_MAP.put("yellow1", IndexedColors.YELLOW1);
    }

}
