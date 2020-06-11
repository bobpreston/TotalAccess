BEGIN {

    # These can also be specified on the command line.
    IT = "rest";
    FS = "[\t]";
    
    # Put everything in DEFPROPS:
    DEFPROPS["restQuery"] = "/_json/bpi/*|/_json/time/updated";
    DEFPROPS["restIndent"] = 1;
    DEFPROPS["httpUrl"] = "http://api.coindesk.com/v1/bpi/currentprice.json";
    DEFPROPS["httpHeader.Accept"] = "application/json";
    
    
    print "Bitcoin prices:";
}

# Check for invalid response code
isXmlNulNode(_$NODE) {
    print getXmlNodeValue(_$NODE);
    exit;
}


/^updated$/ {
    node = getXmlNode(_$NODE, "text()");
    print $0, "=", getXmlNodeValue(node);
	next;
}


# else
{
    node = getXmlNode(_$NODE, "./rate/text()");
    print $0, getXmlNodeValue(node);
}
