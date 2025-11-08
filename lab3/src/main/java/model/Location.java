package model;

public class Location {
    private final double lat;
    private final double lon;
    private final String name;
    private final String country;
    private final String state;
    private final String firstObject;
    private final String secondObject;

    public Location(double lat, double lon, String name, String country, String state, String firstObject, String secondObject) {
        this.lat = lat;
        this.lon = lon;
        this.name = name;
        this.country = country;
        this.state = state;
        this.firstObject = firstObject;
        this.secondObject = secondObject;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getState() {
        return state;
    }

    public String getFirstObject() {
        return firstObject;
    }

    public String getSecondObject() {
        return secondObject;
    }
}
