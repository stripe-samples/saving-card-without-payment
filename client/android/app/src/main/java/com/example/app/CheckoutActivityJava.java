package com.example.app;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.stripe.android.ApiResultCallback;
import com.stripe.android.SetupIntentResult;
import com.stripe.android.Stripe;
import com.stripe.android.model.ConfirmSetupIntentParams;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.model.SetupIntent;
import com.stripe.android.view.CardInputWidget;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class CheckoutActivityJava extends AppCompatActivity {
    /**
     * To run this app, you'll need to first run the sample server locally.
     * Follow the "How to run locally" instructions in the root directory's README.md to get started.
     * Once you've started the server, open http://localhost:4242 in your browser to check that the
     * server is running locally.
     * After verifying the sample server is running locally, build and run the app using the
     * Android emulator.
     */
    // 10.0.2.2 is the Android emulator's alias to localhost
    private static final String backendUrl = "http://10.0.2.2:4242/";
    private OkHttpClient httpClient = new OkHttpClient();
    private String setupIntentClientSecret;
    private Stripe stripe;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        loadPage();
    }

    private void loadPage() {
        // Create a SetupIntent by calling the sample server's /create-setup-intent endpoint.
        MediaType mediaType = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create("", mediaType);
        Request request = new Request.Builder()
                .url(backendUrl + "create-setup-intent")
                .post(body)
                .build();
        httpClient.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> {
                            Context applicationContext = getApplicationContext();
                            Toast.makeText(applicationContext, "Error: " + e.toString(), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Context applicationContext = getApplicationContext();
                                Toast.makeText(applicationContext, "Error: " + response.toString(), Toast.LENGTH_LONG).show();
                            });
                        } else {
                            Gson gson = new Gson();
                            Type type = new TypeToken<Map<String, String>>(){}.getType();
                            Map<String, String> responseMap = gson.fromJson(response.body().string(), type);

                            // The response from the server includes the Stripe publishable key and
                            // SetupIntent details.
                            setupIntentClientSecret = responseMap.get("clientSecret");

                            // Use the key from the server to initialize the Stripe instance.
                            stripe = new Stripe(getApplicationContext(), responseMap.get("publishableKey"));
                        }
                    }
                });

        // Hook up the pay button to the card widget and stripe instance
        Button payButton = findViewById(R.id.payButton);
        payButton.setOnClickListener((View view) -> {
            // Collect card details
            CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
            PaymentMethodCreateParams.Card card = cardInputWidget.getPaymentMethodCard();

            // This example collects the customer's email to know which customer the PaymentMethod belongs to, but your app might use an account id, session cookie, etc.
            EditText emailInput = findViewById(R.id.emailInput);
            PaymentMethod.BillingDetails billingDetails = (new PaymentMethod.BillingDetails.Builder())
                    .setEmail(emailInput.getText().toString())
                    .build();
            if (card != null) {
                // Create SetupIntent confirm parameters with the above
                PaymentMethodCreateParams paymentMethodParams = PaymentMethodCreateParams
                        .create(card, billingDetails);
                ConfirmSetupIntentParams confirmParams = ConfirmSetupIntentParams
                        .create(paymentMethodParams, setupIntentClientSecret);
                stripe.confirmSetupIntent(this, confirmParams);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WeakReference<Activity> weakActivity = new WeakReference<>(this);

        // Handle the result of stripe.confirmSetupIntent
        stripe.onSetupResult(requestCode, data, new ApiResultCallback<SetupIntentResult>() {
            @Override
            public void onSuccess(@NonNull SetupIntentResult result) {
                SetupIntent setupIntent = result.getIntent();
                SetupIntent.Status status = setupIntent.getStatus();
                if (status == SetupIntent.Status.Succeeded) {
                    // Setup completed successfully
                    runOnUiThread(() -> {
                        if (weakActivity.get() != null) {
                            Activity activity = weakActivity.get();
                            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                            builder.setTitle("Setup completed");
                            Gson gson = new GsonBuilder().setPrettyPrinting().create();
                            builder.setMessage(gson.toJson(setupIntent));
                            builder.setPositiveButton("Restart demo", (DialogInterface dialog, int index) -> {
                                CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
                                cardInputWidget.clear();
                                EditText emailInput = findViewById(R.id.emailInput);
                                emailInput.setText(null);
                                loadPage();
                            });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                        }
                    });
                } else if (status == SetupIntent.Status.RequiresPaymentMethod) {
                    // Setup failed – allow retrying using a different payment method
                    runOnUiThread(() -> {
                        Activity activity = weakActivity.get();
                        if (activity != null) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                            builder.setTitle("Setup failed");
                            builder.setMessage(setupIntent.getLastSetupError().message);
                            builder.setPositiveButton("Ok", (DialogInterface dialog, int index) -> {
                                CardInputWidget cardInputWidget = findViewById(R.id.cardInputWidget);
                                cardInputWidget.clear();
                                EditText emailInput = findViewById(R.id.emailInput);
                                emailInput.setText(null);
                            });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                        }
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Setup request failed – allow retrying using the same payment method
                runOnUiThread(() -> {
                    Activity activity = weakActivity.get();
                    if (activity != null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage(e.toString());
                        builder.setPositiveButton("Ok", null);
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
            }
        });
    }
}
