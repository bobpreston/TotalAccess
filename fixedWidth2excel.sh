#!/bin/sh
java -jar totalAccess.jar -f fixedWidth2csv.awk fixed_width.txt | java -jar totalAccess.jar -f csv2excel.awk -v excelFileName=fixed_width.xlsx -v headers=false -