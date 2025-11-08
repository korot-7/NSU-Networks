package workApi;

import model.ClosestObject;
import parserJSON.ParserJSON;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GetPlacesAPI {
    private static final int SEARCH_RADIUS_METERS = 2000;
    public static CompletableFuture<List<ClosestObject>> getPlacesAsync(double lat, double lon, String token) {
        try {
            String uri = "https://catalog.api.2gis.com/3.0/items?point=" + lat + "," + lon + "&radius=" + SEARCH_RADIUS_METERS + "&key=" + token;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            return WorkAPI.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenComposeAsync(response -> {
                        if (response.statusCode() < WorkAPI.START_SUCCESS_RESPONSE || response.statusCode() >= WorkAPI.END_SUCCESS_RESPONSE) {
                            throw new CompletionException(new RuntimeException("2GIS API error: " + response.statusCode()));
                        }
                        return CompletableFuture.supplyAsync(() -> ParserJSON.parseJsonResponse2GIS(response.body()));
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
