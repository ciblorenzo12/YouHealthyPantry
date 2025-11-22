package com.example.myapplication;

import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;

import okhttp3.Cache;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenFoodFactsApiClient implements BarcodeApiClient {

    private static final String TAG = "OpenFoodFactsApiClient";
    private final OkHttpClient client;
    private final Gson gson;

    public OpenFoodFactsApiClient(File cacheDir) {
        int cacheSize = 10 * 1024 * 1024; // 10 MB
        Cache cache = new Cache(cacheDir, cacheSize);

        this.client = new OkHttpClient.Builder()
                .cache(cache)
                .build();
        this.gson = new Gson();
    }

    @Override
    public ProductResponse getProduct(String barcode) throws IOException {
        String url = "https://world.openfoodfacts.org/api/v0/product/" + barcode + ".json";
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "API call failed with code: " + response.code());
                return null;
            }
            String responseBody = response.body().string();
            return gson.fromJson(responseBody, ProductResponse.class);
        }
    }

    // CORRECTED: Added the missing addProduct method
    public boolean addProduct(String barcode, String productName, String brand, String ingredients) throws IOException {
        String url = "https://world.openfoodfacts.org/cgi/product_jqm2.pl";

        RequestBody formBody = new FormBody.Builder()
                .add("code", barcode)
                .add("product_name", productName)
                .add("brands", brand)
                .add("ingredients_text", ingredients)
                // Required for authenticated write operations, but we can try without for now.
                // .add("user_id", "YOUR_USER_ID") 
                // .add("password", "YOUR_PASSWORD")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("User-Agent", "WhatsOnMyFood - Android - Version 1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to submit product, code: " + response.code());
                return false;
            }

            String responseBody = response.body().string();
            Log.d(TAG, "Add product response: " + responseBody);

            // The response is a simple JSON object, check the status field.
            return responseBody.contains("\"status\":1");
        } catch (Exception e) {
            Log.e(TAG, "Error submitting product", e);
            return false;
        }
    }
}
