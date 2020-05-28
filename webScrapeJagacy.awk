# Print out all Jagacy Software product descriptions:

BEGIN {

        # These can also be specified on the command line.
        IT = "http:httpQuery=/html/body/div/div/div";
        FS = "[\t]";
}


# Check for invalid response code
isXmlNulNode(_$NODE) {
        print getXmlNodeValue(_$NODE);
        next;
}

# else
{
        getXmlNodeArray(_$NODE, "./p", products);
        
        for (i in products) {
            product = getXmlNodeValue(getXmlNode(products[i], "text()"));
            gsub(/[\r\n ]+/, " ", product);
            print i ")", escapeString(product);
            print "";
        }
}
