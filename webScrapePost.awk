BEGIN {

    # These can also be specified on the command line.
    IT = "http:httpQuery=/html/*";
    FS = "[\t]";
}

# Check for invalid response code
isXmlNulNode(_$NODE) {
        print getXmlNodeValue(_$NODE);
        next;
}

# else
$1 ~ /^body=.*$/ {
    # Body is in JSON format.
    print ">>>> body:";
    # Remove HTML tags.
    print createJsonNode(substr(_$NODE, 7, length(_$NODE) - 13), "/_json/form", 1, 1);
    print "";
}
