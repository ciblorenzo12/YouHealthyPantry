package com.example.myapplication;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProductRemoteDataSource {

    private final List<BarcodeApiClient> apiClients;

    public ProductRemoteDataSource(File cacheDir) {
        this.apiClients = Arrays.asList(
                new FoodDataCentralClient(),
                new NutritionixClient(),
                new OpenFoodFactsApiClient(cacheDir)
        );
    }

    public ProductWithDetails getProductByBarcode(String barcode) throws IOException {
        ProductResponse finalResponse = null;
        boolean productFoundButNoIngredients = false;

        for (BarcodeApiClient client : apiClients) {
            try {
                ProductResponse currentResponse = client.getProduct(barcode);
                if (currentResponse != null && currentResponse.status == 1 && currentResponse.product != null) {
                    boolean hasIngredients = (currentResponse.product.ingredientsText != null && !currentResponse.product.ingredientsText.isEmpty()) ||
                                           (currentResponse.product.ingredients != null && currentResponse.product.ingredients.length > 0);
                    if (hasIngredients) {
                        finalResponse = currentResponse;
                        break;
                    } else {
                        productFoundButNoIngredients = true;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (finalResponse == null) {
            if (productFoundButNoIngredients) {
                throw new IOException("Product found, but ingredient information is insufficient.");
            } else {
                return null;
            }
        }

        return responseToProductWithDetails(finalResponse, barcode);
    }

    private ProductWithDetails responseToProductWithDetails(ProductResponse response, String barcode) {
        ProductResponse.ProductData productData = response.product;
        Product product = new Product(
                barcode,
                productData.productName,
                productData.brands,
                productData.quantity,
                productData.imageUrl,
                productData.labels,
                productData.packaging,
                productData.categories,
                productData.servingSize,
                productData.nutriscoreGrade,
                productData.novaGroup,
                productData.ecoscoreGrade
        );

        Nutriments nutriments = null;
        if (productData.nutriments != null) {
            ProductResponse.NutrimentsData d = productData.nutriments;
            nutriments = new Nutriments(barcode,
                d.energy, d.energyKj, d.fat, d.saturatedFat, d.monounsaturatedFat, d.polyunsaturatedFat, d.transFat, d.cholesterol,
                d.carbohydrates, d.sugars, d.addedSugars, d.sucrose, d.glucose, d.fructose, d.lactose, d.maltose, d.maltodextrins, d.starch, d.polyols,
                d.fiber, d.proteins, d.salt, d.sodium, d.alcohol, d.vitaminA, d.vitaminD, d.vitaminE, d.vitaminK, d.vitaminC, d.vitaminB1, d.vitaminB2, d.vitaminPP,
                d.vitaminB6, d.vitaminB9, d.vitaminB12, d.biotin, d.pantothenicAcid, d.silica, d.bicarbonate, d.potassium, d.chloride, d.calcium, d.phosphorus,
                d.iron, d.magnesium, d.zinc, d.copper, d.manganese, d.fluoride, d.selenium, d.chromium, d.molybdenum, d.iodine, d.caffeine, d.taurine,
                d.omega3Fat, d.omega6Fat, d.omega9Fat, d.oleicAcid, d.linoleicAcid, d.gammaLinolenicAcid, d.dihomoGammaLinolenicAcid, d.arachidonicAcid,
                d.alphaLinolenicAcid, d.eicosapentaenoicAcid, d.docosahexaenoicAcid, d.carbonFootprint);
        }

        List<Ingredient> ingredients = new ArrayList<>();
        if (productData.ingredientsText != null && !productData.ingredientsText.isEmpty()) {
            String cleanedText = productData.ingredientsText.replaceAll("\\[[a-zA-Z-]+\\]", "").trim();
            
            List<String> ingredientStrings = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int parenDepth = 0;
            for (char c : cleanedText.toCharArray()) {
                if (c == '(') parenDepth++;
                if (c == ')') parenDepth--;

                if (c == ',' && parenDepth == 0) {
                    ingredientStrings.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            if (current.length() > 0) {
                ingredientStrings.add(current.toString());
            }

            int rank = 0;
            for (String ingredientText : ingredientStrings) {
                String trimmedText = ingredientText.trim();
                if (trimmedText.startsWith(",")) {
                    trimmedText = trimmedText.substring(1).trim();
                }
                trimmedText = trimmedText.replaceAll("_", "");

                if (!trimmedText.isEmpty()) {
                    String formattedText = trimmedText.substring(0, 1).toUpperCase() + trimmedText.substring(1).toLowerCase();
                    ingredients.add(new Ingredient(barcode, formattedText, rank++));
                }
            }
        } else if (productData.ingredients != null) {
            for (ProductResponse.IngredientsData ingredientData : productData.ingredients) {
                if (ingredientData != null && ingredientData.text != null) {
                    String cleanedText = ingredientData.text.replaceAll("\\[[a-zA-Z-]+\\]", "").trim();
                    String formattedText = cleanedText.substring(0, 1).toUpperCase() + cleanedText.substring(1).toLowerCase();
                    ingredients.add(new Ingredient(barcode, formattedText, ingredientData.rank));
                }
            }
        }

        ProductWithDetails productWithDetails = new ProductWithDetails();
        productWithDetails.product = product;
        productWithDetails.nutriments = nutriments;
        productWithDetails.ingredients = ingredients;

        return productWithDetails;
    }
}
