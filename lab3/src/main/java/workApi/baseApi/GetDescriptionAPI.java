package workApi.baseApi;

import parserJSON.ParserJSON;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class GetDescriptionAPI extends BaseAPI {
    public static CompletableFuture<String> getPlaceDescriptionAsync(String name) {
        try {
            String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String uri = "https://ru.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=true&explaintext=true&titles="
                    + encodedName + "&format=json";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).header("User-Agent", "lab_3")
                    .GET()
                    .build();

            return executeApi(request, ParserJSON::parseJsonResponseWIKI);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
