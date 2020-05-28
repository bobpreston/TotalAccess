
BEGIN {

        # These can also be specified on the command line.
        IT = "csv";
        FS = "[\t]";

        # Read in sql.

        loadArray(insertSql, "customer_sql.xml");
        insertSql["sql"] = insertSql["insertSql"];
        
        copyArray(deleteSql, insertSql);
        deleteSql["sql"] = deleteSql["deleteSql"];
}

(NR == 1)  {

        # Delete all rows from table.
        closeDatabaseStatement(updateDatabase(deleteSql));
        
        # Ignore headers.
        next;
        
}


# Ignore empty lines.
($0 == "null")    { next;}

# else
        {
        
        # Insert row.
        insertSql[":id"] = NR - 1;
        insertSql[":firstName"] = $1;
        insertSql[":lastName"] = $2;
        insertSql[":zip"] = integer(IT == "text" ? $3 : $8);
        handle = updateDatabase(insertSql);
        
}
    
END {

        print "Processed", NR, "row(s)";
        
        if (handle != "") {
            closeDatabaseStatement(handle);
        }

}
