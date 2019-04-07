#!/bin/bash

if [ ! -d "gsod_data" ]; then
	mkdir -p "gsod_data"
fi

for year in {1929..2019}; do
	if [ ! -d "$year" ]; then
    		echo "Creating a directory."
    		mkdir -p $year
	fi

	echo "Download the data"
	if [ ! -f "$year/gsod_$year.tar" ]; then
    		wget ftp://ftp.ncdc.noaa.gov/pub/data/gsod/$year/gsod_$year.tar -O $year/gsod_$year.tar
	fi

	echo "Unzip the data"
	if [ -f "$year/gsod_$year.tar" ]; then
    		tar -xf $year/gsod_$year.tar -C $year/
    		rm $year/gsod_$year.tar
    		for filename in $year/*.gz; do
        		gunzip $filename
    		done
	fi
	
	echo "strip headers and combine"
	for file in $year/*.op;
	do
                sed '1'd $file > $file.tmp
                mv $file.tmp $file
                cat $file >> $year.op
	done
	
	echo "Parsing op file to csv"
	in2csv -s gsod_schema.csv $year.op > $year.csv
    	rm $year.op
	rm -rf $year
	mv $year.csv gsod_data/
done
