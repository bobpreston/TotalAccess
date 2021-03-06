# Creates a Pivot Table:
BEGIN   {

        # These can also be specified on the command line.
        IT = "excel:sheet=real_estate";
        FS = "[\t]";

        loadArray(querySql, "customer_sql.xml");
        querySql["sqlQuery"] = querySql["sqlQueryRow"];
        
        # Use sorted array.
        createSortedArray(zips);
}

# Skip headers
(FNR == 1)  {
        next;
}

# Check for empty line
($0 == "null") {
        next;
}

# else
{
        zips[$3]++;
}

END {
        # Tell compiler that this is an array.
        customer[0];
        
        # Find database entries with zip codes that are in the excel file.
        for (zip in zips) {
            zip = integer(zip);
            querySql[":zip"] = zip;
            statementHandle = openDatabaseQuery(querySql);
            while (getDatabaseRow(statementHandle, "", customer) != "\0") {
                print customer;
            }
        }
        
        if (statementHandle != "") {
            closeDatabaseStatement(statementHandle);
        }
}
