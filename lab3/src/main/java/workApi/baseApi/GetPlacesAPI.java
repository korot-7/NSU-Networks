package workApi.baseApi;

import model.ClosestObject;
import parserJSON.ParserJSON;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GetPlacesAPI extends BaseAPI {
    private static final int SEARCH_RADIUS_METERS = 2000;
    public static CompletableFuture<List<ClosestObject>> getPlacesAsync(double lat, double lon, String token) {
        try {
            String uri = "https://catalog.api.2gis.com/3.0/items?point=" + lat + "," + lon + "&radius=" + SEARCH_RADIUS_METERS + "&key=" + token;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();

            return executeApi(request, ParserJSON::parseJsonResponse2GIS);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
