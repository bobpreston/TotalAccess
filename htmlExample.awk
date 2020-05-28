# Print out each Jagacy Total Access license agreement (see license_ta.html):

BEGIN {

    # These can also be specified on the command line.
    IT = "html:htmlQuery=/html/body/p,word2000=true";
    FS = "[\t]";
}

!isXmlNulNode(licenseTitleNode = getXmlNode(_$NODE, "./b/a/text()")) {
    description = "";
    
    print "";
    print getXmlNodeValue(licenseTitleNode);
    
    # Print first line:
    getXmlNodeArray(_$NODE, ".//font/text()|.//span/text()", descriptionArray);
    for (i in descriptionArray) {
        value = trim(getXmlNodeValue(descriptionArray[i]));
        gsub(/[\r\n ]+/, " ", value);
        description = description " " value;
    }
    print description;
    description = "";
    
    # Print second line:
    nextNode = getXmlNode(_$NODE, "./following-sibling::p");
    getXmlNodeArray(nextNode, ".//font/text()", descriptionArray);
    for (i in descriptionArray) {
        value = trim(getXmlNodeValue(descriptionArray[i]));
        gsub(/[\r\n ]+/, " ", value);
        description = description " " value;
    }
    print description;
}
