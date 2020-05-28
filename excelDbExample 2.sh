#!/bin/sh
java -cp totalAccess.jar;hsqldb.jar com.jagacy.totalAccess.Main -f excelDbExample2.awk IT='excel:sheet=real_estate' FS='[\t]' real_estate_full.xlsx