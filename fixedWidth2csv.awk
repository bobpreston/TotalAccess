BEGIN {
        FS = "[\t]";
        
        # Tell compiler that this is an array.
        widths[0];
        
        createListArray(widths, 0, 6, 2, 2, 2, 10, 2, 10, 3, 24, 3, 2, 3, 3, 1, 8, 2, 3, 8, 10, 10, 10, 4, 3);
}

# Parse each fixed width line.
{
        $0 = parseFixedLine($0, widths);
}

# Check for valid entry.
(integer($1) != 0) {
        line = "";
        isFirst = 1;
        for (i = 1; i <= NF; i++) {
        
            # Skip white space.
            if ((i % 2) == 0) {
                continue;
            }
        
            $i = escapeCsv(trim($i));
        
            if (!isFirst) {
                line = line ",";
            }
            isFirst = 0;
        
            line = line $i;
        }
    
        print line;
}
