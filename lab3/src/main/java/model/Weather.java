package model;

public class Weather {
    private final String mainWeatherDescription;
    private final double temp;
    private final double feelsLike;
    private final double pressure;
    private final int humidity;

    public Weather(String mainWeatherDescription, double temp, double feelsLike, double pressure, int humidity) {
        this.mainWeatherDescription = mainWeatherDescription;
        this.temp = temp;
        this.feelsLike = feelsLike;
        this.pressure = pressure;
        this.humidity = humidity;
    }

    public String getMainWeatherDescription() {
        return mainWeatherDescription;
    }

    public double getTemp() {
        return temp;
    }

    public double getFeelsLike() {
        return feelsLike;
    }

    public double getPressure() {
        return pressure;
    }

    public int getHumidity() {
        return humidity;
    }
}
