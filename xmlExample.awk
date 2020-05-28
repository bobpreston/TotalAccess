BEGIN {
        # These can also be specified on the command line.
        IT = "xml:xmlQuery=/nutrition/*";
        FS = "[\t]";
        
        # Tell compiler that this is an array.
        attrs[0];
}


($1 == "daily-values")  {
        node = getXmlNode(_$NODE, "sodium");
        
        getXmlNodeAttrs(node, attrs);

        units = attrs["units"];
        
        dailySodium = string(getXmlNode(node, "text()"));
}


($1 == "food") {
        node = getXmlNode(_$NODE, "sodium/text()");
        sodium += getXmlNodeValue(node);
}


END {
        print "Total sodium =", sodium, units;
        print "Daily value =", dailySodium, units;
}
