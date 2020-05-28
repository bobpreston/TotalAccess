# Create an xlsx file by querying the customer table.

BEGIN {

        # These can also be specified on the command line.
        IT = "database::zipStart=00000,:zipEnd=99999";
        FS = "[\t]";

        excelFileName = "customer_db.xlsx";
        print date(), "Creating", excelFileName;
        deleteFile(excelFileName);
        handle = openExcel(excelFileName);
            
        # Tell the compiler that styles is an array.
        styles[0];

        # Create header fonts and styles
        createSortedArray(styles, 1, "underline", "single", "bold", 1);
        createExcelFont(handle, "headerFont", styles);
        createSortedArray(styles, 1, "halign", "center", "useFont", "headerFont");
        createExcelCellStyle(handle, "headerStyle", styles);
        headerSettings["useCellStyle"] = "headerStyle";
}

(FNR == 1)  {

        # Headers
        for (i = 1; i <= NF; i++) {
            closeSettings["autoSizeColumn", i] = 1;
            printExcelValue(handle, FNR, i, $i, headerSettings); 
        }
            
        next;
}

        {
        
        # ID
        printExcelValue(handle, FNR, 1, integer($1));

        # First_Name
        printExcelValue(handle, FNR, 2, $2); 
        
        # Last_Name
        printExcelValue(handle, FNR, 3, $3); 
        
        # Zip
        zip = sprintf("%05d", integer($4));
        printExcelValue(handle, FNR, 4, zip); 
}
    
END {
        print date(), "Processed", FNR, "row(s)";

        closeSettings["activeSheet"] = 1;
        closeExcel(handle, closeSettings);
    
        print date(), "Wrote", excelFileName, "excel file";
}
