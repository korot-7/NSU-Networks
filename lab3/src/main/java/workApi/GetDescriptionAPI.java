package workApi;

import parserJSON.ParserJSON;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GetDescriptionAPI {
    public static CompletableFuture<String> getPlaceDescriptionAsync(String name) {
        try {
            String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String uri = "https://ru.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=true&explaintext=true&titles="
                    + encodedName + "&format=json";

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).header("User-Agent", "lab_3")
                    .GET()
                    .build();

            return WorkAPI.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenComposeAsync(response -> {
                        if (response.statusCode() < WorkAPI.START_SUCCESS_RESPONSE || response.statusCode() >= WorkAPI.END_SUCCESS_RESPONSE) {
                            throw new CompletionException(new RuntimeException("Wiki API error: " + response.statusCode()));
                        }
                        System.out.println("WIKI response for '" + name + "': " + response.body());
                        return CompletableFuture.supplyAsync(() -> ParserJSON.parseJsonResponseWIKI(response.body()));
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
