package workApi;

import model.Location;
import parserJSON.ParserJSON;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class FindLocationAPI {
    private static final int LIMIT = 10;

    public static CompletableFuture<List<Location>> findLocationsAsync(String place, String token) {
        try {
            String uri = "https://graphhopper.com/api/1/geocode?q=" + URLEncoder.encode(place, StandardCharsets.UTF_8) + "&locale=en&limit=" + LIMIT + "&key=" + token;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();

            return WorkAPI.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenComposeAsync(response -> {
                        if (response.statusCode() < WorkAPI.START_SUCCESS_RESPONSE || response.statusCode() >= WorkAPI.END_SUCCESS_RESPONSE) {
                            throw new CompletionException(new RuntimeException("GraphHopper error: " + response.statusCode()));
                        }
                        return CompletableFuture.supplyAsync(() -> ParserJSON.parseJsonResponseGraphHopper(response.body()));
                    });

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
