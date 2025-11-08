package parserJSON;

import com.google.gson.*;
import model.ClosestObject;
import model.Location;
import model.Weather;

import java.util.*;

public class ParserJSON {

    private static final double TEMP_K_TO_C = 273.15;
    private static final double PRES_PA_TO_MM = 0.750062;

    public static List<Location> parseJsonResponseGraphHopper(String json) {
        List<Location> locations = new ArrayList<>();
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray hits = jsonObject.getAsJsonArray("hits");

            for (int i = 0; i < hits.size(); i++) {
                JsonObject hit = hits.get(i).getAsJsonObject();
                JsonObject point = hit.getAsJsonObject("point");

                double lat = point.get("lat").getAsDouble();
                double lon = point.get("lng").getAsDouble();
                String name = hit.get("name").getAsString();
                String country = hit.get("country").getAsString();
                String state = (hit.get("state") != null) ? hit.get("state").getAsString() : "No state";
                String firstObject = hit.get("osm_key").getAsString();
                String secondObject = hit.get("osm_value").getAsString();


                locations.add(new Location(lat, lon, name, country, state, firstObject, secondObject));
            }
        } catch (Exception e) {
            System.out.println("Error JSON GraphHopper: " + e.getMessage());
            System.exit(1);
        }
        return locations;
    }

    public static Weather parseJsonResponseOpenWeather(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonArray weather = jsonObject.getAsJsonArray("weather");
            JsonObject main = jsonObject.getAsJsonObject("main");


            JsonObject weatherObject = weather.get(0).getAsJsonObject();
            String description = weatherObject.get("description").getAsString();


            double temp = main.get("temp").getAsDouble();
            double feelsLike = main.get("feels_like").getAsDouble();
            double pressure = main.get("pressure").getAsInt() * PRES_PA_TO_MM;
            int humidity = main.get("humidity").getAsInt();

            double tempC = temp - TEMP_K_TO_C;
            double feelsLikeC = feelsLike - TEMP_K_TO_C;

            return new Weather(description, tempC, feelsLikeC, pressure, humidity);

        } catch (Exception e) {
            System.out.println("Error JSON OpenWeather: " + e.getMessage());
            System.exit(1);
        }
        return null;
    }

    public static List<ClosestObject> parseJsonResponse2GIS(String json) {
        List<ClosestObject> closestObjectList = new ArrayList<>();
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.getAsJsonObject("result") == null)
                return closestObjectList;
            JsonObject result = jsonObject.getAsJsonObject("result");
            JsonArray items = result.getAsJsonArray("items");

            for (int i = 0; i < items.size(); i++) {
                JsonObject itemObject = items.get(i).getAsJsonObject();
                String name = itemObject.get("full_name").getAsString();
                String type = itemObject.get("type").getAsString();
                String subtype = itemObject.get("subtype") != null ? itemObject.get("subtype").getAsString() : "No subtype";
                closestObjectList.add(new ClosestObject(name, type, subtype));
            }

            for (int i = 0; i < closestObjectList.size(); i++) {
                System.out.println(i + ") " + closestObjectList.get(i).getName() + " - " + closestObjectList.get(i).getSubtype() + " " + closestObjectList.get(i).getType());
            }

            return closestObjectList;

        } catch (Exception e) {
            System.out.println("Error JSON 2GIS: " + e.getMessage());
        }

        return closestObjectList;
    }

    public static String parseJsonResponseWIKI(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            JsonObject query = jsonObject.getAsJsonObject("query");
            JsonObject pages = query.getAsJsonObject("pages");
            for (String key : pages.keySet()) {
                if (Objects.equals(key, "-1")) return "No description";

                JsonObject page = pages.getAsJsonObject(key);
                return page.get("extract").getAsString();
            }
        } catch (Exception e) {
            System.out.println("Error JSON WIKI: " + e.getMessage());
        }

        return null;
    }
}
