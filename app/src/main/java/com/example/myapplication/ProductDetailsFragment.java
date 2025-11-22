package com.example.myapplication;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.analysis.AnalysisResult;
import com.example.myapplication.analysis.AnalysisResultAdapter;
import com.example.myapplication.analysis.ProductAnalysisReport;
import com.example.myapplication.analysis.rules.RuleEngine;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductDetailsFragment extends BottomSheetDialogFragment {

    private static final String ARG_BARCODE = "barcode";

    private ProductRepository productRepository;
    private ExecutorService executorService;
    private AppDatabase db;
    private RuleEngine ruleEngine;
    private FirebaseUser currentUser;

    private ImageView productImageView;
    private TextView productNameTextView, productBrandTextView, packagingTextView, labelsTextView, ingredientsTextView;
    private TextView nutriscoreTextView, novaTextView, ecoscoreTextView, categoriesTextView, servingSizeTextView, healthScoreTextView;
    private Button removeFromPantryButton;
    private TableLayout nutritionFactsTable;
    private RecyclerView analysisRecyclerView;

    public static ProductDetailsFragment newInstance(String barcode) {
        ProductDetailsFragment fragment = new ProductDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_BARCODE, barcode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        productRepository = new ProductRepository(requireActivity().getApplication());
        executorService = Executors.newSingleThreadExecutor();
        db = AppDatabase.getDatabase(requireContext());
        ruleEngine = new RuleEngine();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_details, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setFitToContents(false);
                behavior.setPeekHeight(getResources().getDisplayMetrics().heightPixels / 2);
                behavior.setHideable(true);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        productImageView = view.findViewById(R.id.product_image_view);
        productNameTextView = view.findViewById(R.id.product_name_text_view);
        productBrandTextView = view.findViewById(R.id.product_brand_text_view);
        packagingTextView = view.findViewById(R.id.packaging_text_view);
        labelsTextView = view.findViewById(R.id.labels_text_view);
        ingredientsTextView = view.findViewById(R.id.ingredients_text_view);
        nutriscoreTextView = view.findViewById(R.id.nutriscore_text_view);
        novaTextView = view.findViewById(R.id.nova_text_view);
        ecoscoreTextView = view.findViewById(R.id.ecoscore_text_view);
        categoriesTextView = view.findViewById(R.id.categories_text_view);
        servingSizeTextView = view.findViewById(R.id.serving_size_text_view);
        healthScoreTextView = view.findViewById(R.id.health_score_text_view);
        removeFromPantryButton = view.findViewById(R.id.remove_from_pantry_button);
        nutritionFactsTable = view.findViewById(R.id.nutrition_facts_table);
        analysisRecyclerView = view.findViewById(R.id.analysis_recycler_view);
        analysisRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        nutriscoreTextView.setOnClickListener(v -> showScoreExplanation("Nutri-Score", "A nutritional rating system."));
        novaTextView.setOnClickListener(v -> showScoreExplanation("NOVA Group", "A food processing classification."));
        ecoscoreTextView.setOnClickListener(v -> showScoreExplanation("Eco-Score", "An environmental impact rating."));

        if (currentUser == null) {
            showErrorAndDismiss("Authentication error.");
            return;
        }

        String barcode = getArguments() != null ? getArguments().getString(ARG_BARCODE) : null;
        if (barcode != null) {
            loadProductDetails(barcode);
            removeFromPantryButton.setOnClickListener(v -> {
                executorService.execute(() -> {
                    db.productDao().deletePantryProduct(barcode, currentUser.getUid());
                    if(getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Removed from Pantry", Toast.LENGTH_SHORT).show();
                            checkIfProductInPantry(barcode);
                        });
                    }
                });
            });
        } else {
            showErrorAndDismiss("No barcode provided.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (productRepository != null) {
            productRepository.close();
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private void loadProductDetails(String barcode) {
        productRepository.getProductByBarcode(barcode, new ProductRepository.RepositoryCallback<ProductRepository.ProductResult>() {
            @Override
            public void onComplete(ProductRepository.ProductResult result) {
                if (getActivity() == null || !isAdded()) return;
                getActivity().runOnUiThread(() -> {
                    if (result != null && result.productWithDetails != null) {
                        displayProductDetails(result.productWithDetails);
                        executorService.execute(() -> {
                            // CORRECTED: Removed the invalid character from the constructor call.
                            db.productDao().insertPantry(new Pantry(barcode, currentUser.getUid()));
                            checkIfProductInPantry(barcode);
                        });
                    } else {
                        showAddProductDialog(barcode);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !isAdded()) return;
                getActivity().runOnUiThread(() -> showErrorAndDismiss("Error: " + e.getMessage()));
            }
        });
    }

    private void checkIfProductInPantry(String barcode) {
        executorService.execute(() -> {
            Pantry pantryItem = db.productDao().findPantryItemByBarcode(barcode, currentUser.getUid());
            if(getActivity() != null) {
                getActivity().runOnUiThread(() -> removeFromPantryButton.setVisibility(pantryItem != null ? View.VISIBLE : View.GONE));
            }
        });
    }

    private void displayProductDetails(ProductWithDetails productDetails) {
        if (productDetails.product.imageUrl != null && !productDetails.product.imageUrl.isEmpty()) {
            Picasso.get().load(productDetails.product.imageUrl).into(productImageView);
        }

        productNameTextView.setText(productDetails.product.productName != null ? productDetails.product.productName : "N/A");
        productBrandTextView.setText(productDetails.product.brands != null ? productDetails.product.brands : "");
        packagingTextView.setText(productDetails.product.packaging != null ? productDetails.product.packaging : "");
        labelsTextView.setText(productDetails.product.labels != null ? productDetails.product.labels : "");
        categoriesTextView.setText(productDetails.product.categories != null ? productDetails.product.categories : "");
        servingSizeTextView.setText(productDetails.product.servingSize != null ? productDetails.product.servingSize : "");

        ProductAnalysisReport report = ruleEngine.analyze(productDetails);
        if (report != null) {
            healthScoreTextView.setText(String.format("Health Score: %d/100", report.getOverallScore()));
            analysisRecyclerView.setAdapter(new AnalysisResultAdapter(report.getResults()));
            displayHighlightedIngredients(productDetails, report);
        } else {
            healthScoreTextView.setText("Health Score: N/A");
            analysisRecyclerView.setAdapter(null);
            ingredientsTextView.setText("Could not analyze ingredients.");
        }
        
        setScoreTextView(nutriscoreTextView, productDetails.product.nutriscoreGrade, "Nutri-Score");
        setScoreTextView(novaTextView, productDetails.product.novaGroup, "NOVA Group");
        setScoreTextView(ecoscoreTextView, productDetails.product.ecoscoreGrade, "Eco-Score");

        displayNutriments(productDetails.nutriments);
    }

    private void displayHighlightedIngredients(ProductWithDetails productDetails, ProductAnalysisReport report) {
        if (productDetails.ingredients == null || productDetails.ingredients.isEmpty()) {
            ingredientsTextView.setText("No ingredients listed.");
            return;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (Ingredient ingredient : productDetails.ingredients) {
            if (ingredient.text != null) {
                SpannableString spannable = new SpannableString(ingredient.text);
                for(AnalysisResult res : report.getResults()){
                    if(res.getTriggeringIngredient() != null && ingredient.text.toLowerCase().contains(res.getTriggeringIngredient().toLowerCase())){
                        spannable.setSpan(new BackgroundColorSpan(0x33FF0000), 0, spannable.length(), 0);
                        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, spannable.length(), 0);
                    }
                }
                builder.append(spannable).append("\n");
            }
        }
        ingredientsTextView.setText(builder);
    }

    private void displayNutriments(Nutriments nutriments) {
        nutritionFactsTable.removeAllViews();
        if (nutriments != null) {
            nutritionFactsTable.setVisibility(View.VISIBLE);
            addNutritionRow("Energy", nutriments.energy, "kcal");
            addNutritionRow("Fat", nutriments.fat, "g");
            addNutritionRow("Saturated Fat", nutriments.saturatedFat, "g");
            addNutritionRow("Carbohydrates", nutriments.carbohydrates, "g");
            addNutritionRow("Sugars", nutriments.sugars, "g");
            addNutritionRow("Fiber", nutriments.fiber, "g");
            addNutritionRow("Proteins", nutriments.proteins, "g");
            addNutritionRow("Sodium", nutriments.sodium, "mg");
        } else {
            nutritionFactsTable.setVisibility(View.GONE);
        }
    }

    private void addNutritionRow(String name, Double value, String unit) {
        if (value == null || value == 0.0) return;
        TableRow row = new TableRow(getContext());
        TextView nameView = new TextView(getContext());
        TextView valueView = new TextView(getContext());
        nameView.setText(name);
        valueView.setText(String.format("%.2f %s", value, unit));
        nameView.setPadding(8, 4, 8, 4);
        valueView.setPadding(8, 4, 8, 4);
        valueView.setGravity(Gravity.END);
        row.addView(nameView);
        row.addView(valueView);
        nutritionFactsTable.addView(row);
    }

    private void setScoreTextView(TextView textView, String score, String prefix) {
        if (score == null || score.isEmpty()) {
            textView.setVisibility(View.GONE);
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(String.format("%s: %s", prefix, score.toUpperCase()));
        Drawable background = ContextCompat.getDrawable(requireContext(), R.drawable.score_background);
        if(background != null) {
            DrawableCompat.setTint(background, getScoreColor(score));
            textView.setBackground(background);
        }
    }

    private int getScoreColor(String score) {
        if (score == null) return ContextCompat.getColor(requireContext(), R.color.score_unknown);
        switch (score.toLowerCase()) {
            case "a":
            case "1":
                return ContextCompat.getColor(requireContext(), R.color.nutriscore_a);
            case "b":
                return ContextCompat.getColor(requireContext(), R.color.nutriscore_b);
            case "c":
            case "2":
                return ContextCompat.getColor(requireContext(), R.color.nutriscore_c);
            case "d":
            case "3":
                return ContextCompat.getColor(requireContext(), R.color.nutriscore_d);
            case "e":
            case "4":
                return ContextCompat.getColor(requireContext(), R.color.nutriscore_e);
            default:
                return ContextCompat.getColor(requireContext(), R.color.score_unknown);
        }
    }

    private void showErrorAndDismiss(String message) {
        if(getContext() == null) return;
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        dismiss();
    }

    private void showAddProductDialog(String barcode) {
        if(getContext() == null) return;
        new AlertDialog.Builder(requireContext())
            .setTitle("Product Not Found")
            .setMessage("This product is not in our database yet. Would you like to add it?")
            .setPositiveButton("Add Product", (dialog, which) -> {
                Intent intent = new Intent(getActivity(), AddProductActivity.class);
                intent.putExtra(AddProductActivity.EXTRA_BARCODE, barcode);
                startActivity(intent);
                dismiss();
            })
            .setNegativeButton("Cancel", (dialog, which) -> dismiss())
            .setOnCancelListener(dialog -> dismiss())
            .show();
    }

    private void showScoreExplanation(String title, String message) {
        new AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }
}
