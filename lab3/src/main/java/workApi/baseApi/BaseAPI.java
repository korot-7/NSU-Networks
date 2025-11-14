package workApi.baseApi;

import workApi.WorkAPI;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public abstract class BaseAPI {
    protected static <T> CompletableFuture<T> executeApi(HttpRequest request, Function<String, T> parser) {
        try {
            return WorkAPI.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenComposeAsync(response -> {
                        if (response.statusCode() < WorkAPI.START_SUCCESS_RESPONSE || response.statusCode() >= WorkAPI.END_SUCCESS_RESPONSE) {
                            throw new CompletionException(new RuntimeException("API error: " + response.statusCode()));
                        }
                        return CompletableFuture.supplyAsync(() -> parser.apply(response.body()));
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}