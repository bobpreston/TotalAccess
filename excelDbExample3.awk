# Creates a Pivot Table:
BEGIN   {

        # These can also be specified on the command line.
        IT = "excel:sheet=real_estate";
        FS = "[\t]"; 

        loadArray(querySql, "customer_sql.xml");
        
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

        # Use dynamic sql.
        sql = "SELECT * FROM customer WHERE zip IN (";
        
        first = 1;
        for (i in zips) {
            if (!first) {
                sql = sql ",";
            }
            first = 0;
            
            sql = sql i;
        }
        
        sql = sql ")";
        querySql["sqlQuery"] = sql;
        
        # Tell compiler that this is an array.
        customer[0];
        
        # Use sorted array.
        createSortedArray(customer);

        # Find database entries with zip codes that are in the excel file.
        statementHandle = queryDatabase(querySql, outArrays);
        for (i in outArrays) {
            copyArray(customer, outArrays[i]);
            print customer;
        }

        if (statementHandle != "") {
            closeDatabaseStatement(statementHandle);
        }
}
