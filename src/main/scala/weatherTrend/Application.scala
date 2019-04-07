package weatherTrend

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.{Date, Locale}

import org.apache.commons.math3.stat.regression.SimpleRegression
import org.apache.spark.sql._
import org.apache.spark.sql.functions.{udf, _}

object Application {

  def main(args: Array[String]): Unit = {

    val sparkSession = SparkSession.builder().master("local").appName("Weather Trend").getOrCreate()
    import sparkSession.implicits._

    // Gather user passed parameters, or use a default value
    val zipCode: String = if(args.size == 2) args(0) else "20144"

    val format = new java.text.SimpleDateFormat("yyyy-MM-dd")
    val date: Date = if(args.size ==2) format.parse(args(1)) else format.parse("2019-10-06")
    val localDate = date.toInstant.atZone(ZoneId.systemDefault).toLocalDate

    // Gather the centroid latitude and longitude of the provided zipcode
    val (zipCodeLat, zipCodeLon) = sparkSession.read.option("header","true")
      .csv(this.getClass.getClassLoader.getResource("zip_codes_states.csv").getPath)
      .filter($"zip_code" === zipCode).select($"latitude", $"longitude").collect().map(
      row => (row.getString(0), row.getString(1))
    ).head

    // Find the distance between two lat lons.
    val calculateDistanceInKilometerUdf = udf((latitude: String, longitude : String) => {
      val AVERAGE_RADIUS_OF_EARTH_KM = 6371

      // Data cleanup, since positive values are written as +32.092
      val dfLat = latitude.replaceAll("\\+", "").toFloat
      val dfLon = longitude.replaceAll("\\+", "").toFloat

      val latDistance = Math.toRadians(dfLat- zipCodeLat.toFloat)
      val lngDistance = Math.toRadians(dfLon - zipCodeLon.toFloat)
      val sinLat = Math.sin(latDistance / 2)
      val sinLng = Math.sin(lngDistance / 2)
      val a = sinLat * sinLat +
        (Math.cos(Math.toRadians(dfLat)) *
          Math.cos(Math.toRadians(zipCodeLat.toFloat)) *
          sinLng * sinLng)
      val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
      (AVERAGE_RADIUS_OF_EARTH_KM * c).toInt
    })


    // Collection of USAF (aka weather station) locations, with a mixin of distance from the provided zip code
    val stationLocations = sparkSession.read.option("header", "true")
      .csv(this.getClass.getClassLoader.getResource("isd-history.csv").getPath)
      .filter("USAF is not null")
      .filter("USAF != 999999")
      .filter("LAT is not null")
      .filter("LON is not null")
      .select($"USAF", $"LAT", $"LON")
      .withColumn("distanceFromZip", calculateDistanceInKilometerUdf($"LAT",$"LON"))
      .orderBy(asc("distanceFromZip"))

    val closestStationToZip: String = stationLocations.select($"USAF").take(1).head.getString(0)

    // Grab all of the gziped gsod weather data from NOAA
    val gsod = sparkSession.read.option("header", "true")
      .csv(this.getClass.getClassLoader.getResource("gsod_data").getPath +"/*")

    // Filter by the closest station and the day that we care about
    val filteredData = gsod.select($"STN---" as "stationNumber",$"WBAN",$"YEAR",$"MO",$"DA", $"TEMP")
      .filter($"stationNumber" === closestStationToZip)
      .filter(($"MO" === localDate.getMonthValue) && ($"DA" === localDate.getDayOfMonth))
      .select($"YEAR", $"TEMP")

    // Fit a linear regression, the slope will show the trend for that day
    val regression = new SimpleRegression()

    filteredData.collect().foreach(row => {
      val year = row.getString(0).toInt
      val temp = row.getString(1).toDouble
      regression.addData(year,temp)
    })

    val trend = regression.getSlope.compareTo(0) match {
      case -1 => "decreasing"
      case 0 => "level"
      case 1 => "increasing"
    }

    println(s"For the zipcode: ${zipCode}, the weather has been having a(n) ${trend} temperature trend on " +
      s"${localDate.getMonth.getDisplayName(TextStyle.SHORT,Locale.US)} ${localDate.getDayOfMonth}")

    val output = (s"For the zipcode: ${zipCode}, the weather has been having a(n) ${trend} temperature trend on " +
      s"${localDate.getMonth.getDisplayName(TextStyle.SHORT,Locale.US)} ${localDate.getDayOfMonth}")

    Files.write(Paths.get(s"weatherTrends${zipCode}_${localDate.toString}.txt"), output.getBytes(StandardCharsets.UTF_8))

  }

}
