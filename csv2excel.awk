# Convert a csv file to an xls/xlsx file with formatting and sums/averages of numbers.

BEGIN {
        # These can also be specified on the command line.
        IT = "csv";
        FS = "[\t]";
        
        # Tell the compiler that styles is an array.
        styles[0];
}

(FNR == 1)  {

        # excelFileName may be passed on the command line
        if (excelFileName == "") {
            excelFileName = FILENAME;
            sub(/\.csv$/, ".xls", excelFileName);
            ##sub(/\.csv$/, ".xlsx", excelFileName);
        }
        if (FILENAME == "") {
            print date(), "Converting to", excelFileName;
        } else {
            print date(), "Converting", FILENAME, "to", excelFileName;
        }
        
        if (excelFileName !~ /^:stream:/) {
            deleteFile(excelFileName);
        }
        
        handle = openExcel(excelFileName);
            
        # Create fonts and styles
            
        createSortedArray(styles, 1, "dataFormat", 0x02);
        createExcelCellStyle(handle, "doubleStyle", styles);
        doubleSettings["useCellStyle"] = "doubleStyle";
    
        createSortedArray(styles, 1, "dataFormat", 0x0e);
        createExcelCellStyle(handle, "dateStyle", styles);
        dateSettings["useCellStyle"] = "dateStyle";
    
        createSortedArray(styles, 1, "halign", "center");
        createExcelCellStyle(handle, "booleanStyle", styles);
        booleanSettings["useCellStyle"] = "booleanStyle";

        createSortedArray(styles, 1, "underline", "single", "bold", 1);
        createExcelFont(handle, "headerFont", styles);
        createSortedArray(styles, 1, "halign", "center", "useFont", "headerFont");
        createExcelCellStyle(handle, "headerStyle", styles);
        headerSettings["useCellStyle"] = "headerStyle";

                
        for (i = 1; i <= NF; i++) {
            closeSettings["autoSizeColumn", i] = 1;
        }
            
        # Headers
        if ((headers != 0) && (headers != "false")) {
            for (i = 1; i <= NF; i++) {
                printExcelValue(handle, FNR, i, $i, headerSettings); 
            }
            
            next;
        }
}

# Ignore empty lines.
($0 == "null")    { next;}

# else
        {
            
        for (i = 1; i <= NF; i++) {
            # Check for integer
            if ($i ~ /^[+-]?\d+$/) {
            
                value = integer($i);
                    
                # Use default settings.
                printExcelValue(handle, FNR, i, value); 

                numericColumns[i] += value;
                    
            # Check for double
            } else if ($i ~ /^[+-]?[0-9,]+\.\d+$/) {
                
                # Remove commas
                sub(/,/, "", $i);
                    
                value = double($i);
                printExcelValue(handle, FNR, i, value, doubleSettings); 
                    
                numericColumns[i] += value;
                    
            # Check for date
            } else if ($i ~ /^[A-Za-z]{3} [A-Za-z]{3} \d{1,2} \d{1,2}:\d{1,2}:\d{1,2} [A-Z]{3} \d{4}/) {

                printExcelDate(handle, FNR, i, $i, dateSettings, "EEE MMM d HH:mm:ss z yyyy"); 
               
            # Check for boolean
            } else if (($i == "TRUE") || ($i == "FALSE")) {
                    
                value = ($i == "TRUE") ? 1 : 0;
                printExcelBoolean(handle, FNR, i, value, booleanSettings); 
                    
            # string
            } else {
                    
                value = $i;
                    
                # Use default settings.
                printExcelValue(handle, FNR, i, value); 
                    
            }
        }
}
    
END {

        print "Processed", FNR, "row(s)";

        for (i in numericColumns) {
            column = substr("ABCDEFGHIJKLMNOPQRSTUVWXYZ", i, 1);
            
            formula = "SUM(" column "1:" column FNR ")";
            printExcelFormula(handle, FNR + 1, i, formula, doubleSettings);
            
            formula = "AVERAGE(" column "1:" column FNR ")";
            printExcelFormula(handle, FNR + 2, i, formula, doubleSettings);
            
            delete closeSettings["autoSizeColumn", i];
            closeSettings["columnWidth", i] = (length(sprintf("%.02f", 
                double(numericColumns[i]))) + 1) * 256;
        }

        closeSettings["activeSheet"] = 1;
        closeExcel(handle, closeSettings);
    
        print date(), "Wrote", excelFileName, "excel file";
}
