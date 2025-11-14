package workApi.baseApi;

import model.Weather;
import parserJSON.ParserJSON;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;

public class GetWeatherAPI extends BaseAPI {
    public static CompletableFuture<Weather> getWeatherAsync(double lat, double lon, String token) {
        try {
            String uri = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + token;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            return executeApi(request, ParserJSON::parseJsonResponseOpenWeather);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
