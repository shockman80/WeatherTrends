# WeatherTrends

Using a provided zip code and date, glean from GSOD (Global Surface Summary of the Day) data from NOAA (National Oceanic and Atmospheric Administration). 

GSOD is collected by weather stations. Geolocations for each weather stations are gathered from the Integrated Surface Database.

The nearest weather station to the provided zip code is found using the latitude and longitude of the center of the zip code with the geolocation provided in the ISD file.

To gather the GSOD data, one can run the get_data.sh script or download the tar.gz file located https://drive.google.com/file/d/1-W8MxO5q27-Bd7D2QfJ6HcDvLdQ7hzZU/view?usp=sharing .
Extract the data in the src/main/resources directory.

The spark application can be run without parameters to get the default zipcode of 21044 and the date of 2019-10-06.

The Docker file can be run using:
```
docker pull danguderian/spark_image:validation
docker-compose build
docker-compose up -d --scale slave=3
```
Wait a minute for all the services to spin up and run

```
docker exec master /opt/spark/bin/spark-submit \
                        --class weatherTrend.Application \
                        --master spark://master:6066 \
                        --deploy-mode cluster \
                        --verbose \
                        /opt/weather-trends-1.0-SNAPSHOT.jar
```
Once the job is done running:
```
docker-compose down
```
