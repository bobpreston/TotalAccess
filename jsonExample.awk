BEGIN {
        # These can also be specified on the command line.
        IT = "json:jsonQuery=/_json/_array/_record/name";
        FS = "[\t]";
}

{
	print $1;
}