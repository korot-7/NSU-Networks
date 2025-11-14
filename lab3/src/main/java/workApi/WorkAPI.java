package workApi;

import model.*;
import workApi.baseApi.GetDescriptionAPI;
import workApi.baseApi.GetPlacesAPI;
import workApi.baseApi.GetWeatherAPI;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WorkAPI {
    public final static HttpClient client = HttpClient.newHttpClient();
    public static final int START_SUCCESS_RESPONSE = 200;
    public static final int END_SUCCESS_RESPONSE = 300;

    public static CompletableFuture<FinalObject> placeInfoAsync(Location location, String tokenWeather, String tokenPlaces) {
        CompletableFuture<Weather> weatherFuture = GetWeatherAPI.getWeatherAsync(location.getLat(), location.getLon(), tokenWeather);
        CompletableFuture<List<ClosestObject>> placesFuture = GetPlacesAPI.getPlacesAsync(location.getLat(), location.getLon(), tokenPlaces);

        CompletableFuture<List<ObjectWithDescription>> descriptionsFuture = placesFuture.thenCompose(places -> {
            if (places.isEmpty()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            List<CompletableFuture<ObjectWithDescription>> listFutObjWithDesc = new ArrayList<>();
            for (ClosestObject place : places) {
                CompletableFuture<ObjectWithDescription> futObjWithDesc = GetDescriptionAPI.getPlaceDescriptionAsync(place.getName())
                        .thenApply(desc -> new ObjectWithDescription(place, desc));
                listFutObjWithDesc.add(futObjWithDesc);
            }

            return CompletableFuture.allOf(listFutObjWithDesc.toArray(new CompletableFuture[0]))
                    .thenApply(_ -> listFutObjWithDesc.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        });

        return descriptionsFuture.thenCombineAsync(weatherFuture,
                (descs, weather) -> new FinalObject(location, weather, descs));
    }
}