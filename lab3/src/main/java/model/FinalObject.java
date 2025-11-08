package model;

import java.util.List;

public class FinalObject {
    Location location;
    Weather  weather;
    List<ObjectWithDescription> placesWithDescription;

    public FinalObject(Location location, Weather weather, List<ObjectWithDescription> placesWithDescription) {
        this.location = location;
        this.weather = weather;
        this.placesWithDescription = placesWithDescription;
    }

    public Location getLocation() {
        return location;
    }

    public Weather getWeather() {
        return weather;
    }

    public List<ObjectWithDescription> getPlacesWithDescription() {
        return placesWithDescription;
    }
}
