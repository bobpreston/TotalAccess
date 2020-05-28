BEGIN {

    # These can also be specified on the command line.
    IT = "rest:restQuery=/_json/_array/_record,restIndent=true";
    FS = "[\t]";
}

# Check for invalid response code
isXmlNulNode(_$NODE) {
        print getXmlNodeValue(_$NODE);
        next;
}

# else
{
    print ">>>>", $1 ":";
    print xmlToJsonString(_$NODE, 2);
}