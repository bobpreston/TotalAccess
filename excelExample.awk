# Creates a Pivot Table.
BEGIN   {

        # These can also be specified on the command line.
        IT = "excel:sheet=real_estate";
        FS = "[\t]";

        # Use sorted arrays
        createSortedArray(zipBeds);
        createSortedArray(zipBaths);
        createSortedArray(totalBeds);
        createSortedArray(totalBaths);
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
        zip = $3;
        beds = $5;
        baths = $6;
    
        zipBeds[zip, beds]++;
        zipBaths[zip, baths]++;
        
        totalBeds[beds]++;
        totalBaths[baths]++;
}

END {

        print "Processed", FNR, "row(s)";

        for (i in zipBeds) {
            split(i, a, SUBSEP);
            print "Number of places with", a[2], "bedroom(s) for zip code", a[1], "is", zipBeds[i];
        }
        print "";
        
        for (i in zipBaths) {
            split(i, a, SUBSEP);
            print "Number of places with", a[2], "bath(s) for zip code", a[1], "is", zipBaths[i];
        }
        print "";
        
        for (i in totalBeds) {
            print "Total number of places with", i, "bedroom(s) is", totalBeds[i];
        }
        print "";
        
        for (i in totalBaths) {
            print "Total number of places with", i, "bath(s) is", totalBaths[i];
        }
        print "";
}
