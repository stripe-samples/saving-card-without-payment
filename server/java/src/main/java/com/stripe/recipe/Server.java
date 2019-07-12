package com.stripe.recipe;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.SetupIntent;
import com.stripe.exception.*;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;

public class Server {
    private static Gson gson = new Gson();

    static class PostBody {
        @SerializedName("some_field")
        String someField;

        public String getSomeField() {
            return someField;
        }
    }

    public static void main(String[] args) {
        Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().getParent().getParent().toString() + "/client")
                        .toAbsolutePath().toString());

        get("/", (request, response) -> {
            response.type("application/json");
            return "hello world";
        });

        get("/public-key", (request, response) -> {
            response.type("application/json");
            JsonObject publiKey = new JsonObject();
            publiKey.addProperty("publicKey", System.getenv("STRIPE_PUBLIC_KEY"));
            return publiKey.toString();
        });

        get("/create-setup-intent", (request, response) -> {
            response.type("application/json");

            Map<String, Object> params = new HashMap<>();
            SetupIntent setupIntent = SetupIntent.create(params);

            return gson.toJson(setupIntent);
        });

        post("/create-customer", (request, response) -> {
            response.type("application/json");

            SetupIntent setupIntent = ApiResource.GSON.fromJson(request.body(), SetupIntent.class);
            // This creates a new Customer and attaches the PaymentMethod in one API call.
            // This creates a new Customer and attaches the PaymentMethod in one API call.
            Map<String, Object> customerParams = new HashMap<String, Object>();
            customerParams.put("payment_method", setupIntent.getPaymentMethod());
            Customer customer = Customer.create(customerParams);
            return customer.toJson();
        });

        post("/webhook", (request, response) -> {
            System.out.println("Webhook");
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");
            String endpointSecret = System.getenv("STRIPE_WEBHOOK_SECRET");

            Event event = null;

            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                // Invalid signature
                response.status(400);
                return "";
            }

            switch (event.getType()) {
            case "setup_intent.created":
                System.out.println("Received event");
                break;
            case "setup_intent.succeeded":
                System.out.println("Received event");
                break;
            case "setup_intent.setup_failed":
                System.out.println("Received event");
                break;
            default:
                // Unexpected event type
                response.status(400);
                return "";
            }

            response.status(200);
            return "";
        });
    }
}