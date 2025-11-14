package workApi.baseApi;

import model.Location;
import parserJSON.ParserJSON;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FindLocationAPI extends BaseAPI {
    private static final int LIMIT = 10;

    public static CompletableFuture<List<Location>> findLocationsAsync(String place, String token) {
        try {
            String uri = "https://graphhopper.com/api/1/geocode?q=" + URLEncoder.encode(place, StandardCharsets.UTF_8) + "&locale=en&limit=" + LIMIT + "&key=" + token;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();

            return executeApi(request, ParserJSON::parseJsonResponseGraphHopper);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
