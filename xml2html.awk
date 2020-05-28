BEGIN {
        # These can also be specified on the command line.
        IT = "xml:xmlQuery=/nutrition/*";
        FS = "[\t]";
}


($1 == "daily-values")  {
        context["daily-values"] = _$NODE;
}


($1 == "food") {
        nameTextNode = getXmlNode(_$NODE, "name/text()");
        
        name = getXmlNodeValue(nameTextNode);
        name = escapeHtml(trim(name));
        
        foodContext[name] = _$NODE;
}



END {
        context["food"] = foodContext;
        
        if (htmlFileName == "") {
            html = mergeVelocityTemplate("xml2html.vm", context);
            print html;
        } else {
            if (path != "") {
                initProps["file.resource.loader.path"] = path;
            }
            mergeVelocityTemplate("xml2html.vm", context, htmlFileName, initProps);
        }
}

