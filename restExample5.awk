BEGIN {

    # These can also be specified on the command line.
    IT = "rest";
    FS = "[\t]";
    
    # Put everything in DEFPROPS:
    DEFPROPS["restQuery"] = "/_json/countries";
    DEFPROPS["restIndent"] = 1;
    DEFPROPS["httpUrl"] = "http://54.72.28.201:80/1.0/countries";
    DEFPROPS["httpHeader.Accept"] = "application/json";
	
	populationProps["httpHeader.Accept"] = "application/json";
}


# Check for invalid response code
isXmlNulNode(_$NODE) {
    print getXmlNodeValue(_$NODE);
    exit;
}


# else
{ 
    country = getXmlNodeValue(getXmlNode(_$NODE, "text()")); 
	if (country ~/^[A-Z ]+$/) {
		# Skip continents
		print "Skipping continent", country;
		next;
	}
	
	countryUrl = encodeUrl(country);
	gsub(/\+/, "%20", countryUrl);
	populationProps["httpUrl"] = "http://54.72.28.201:80/1.0/population/" countryUrl "/today-and-tomorrow";
	
	loadRestNodeArray(populationProps, "_json/total_population/population/text()", populationNodeArray);
	if (arrayLength(populationNodeArray) != 2) {
		print "Skipping", country;
		next;
	}
	
    delta = getXmlNodeValue(populationNodeArray[2]) - getXmlNodeValue(populationNodeArray[1]);
	print country, ":", delta;
	total += delta;
}

END {
	print "Population will grow by approx.", total, "persons by tomorrow";
}
