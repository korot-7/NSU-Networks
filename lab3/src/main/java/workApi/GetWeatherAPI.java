package workApi;

import model.Weather;
import parserJSON.ParserJSON;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GetWeatherAPI {
    public static CompletableFuture<Weather> getWeatherAsync(double lat, double lon, String token) {
        try {
            String uri = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + token;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            return WorkAPI.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenComposeAsync(response -> {
                        if (response.statusCode() < WorkAPI.START_SUCCESS_RESPONSE || response.statusCode() >= WorkAPI.END_SUCCESS_RESPONSE) {
                            throw new CompletionException(new RuntimeException("OpenWeather error: " + response.statusCode()));
                        }
                        return CompletableFuture.supplyAsync(() -> ParserJSON.parseJsonResponseOpenWeather(response.body()));
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
