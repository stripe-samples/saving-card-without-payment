package com.stripe.sample;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.staticFiles;
import static spark.Spark.port;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Event;
import com.stripe.model.SetupIntent;
import com.stripe.exception.*;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.model.EventDataObjectDeserializer;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();

    public static void main(String[] args) {
        port(4242);
        Dotenv dotenv = Dotenv.load();

        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        post("/create-setup-intent", (request, response) -> {
            response.type("application/json");

            // Create or use an existing Customer to associate with the SetupIntent.
            // The PaymentMethod will be stored to this Customer for later use.
            Customer customer = Customer
                    .create(new CustomerCreateParams.Builder().build());

            SetupIntentCreateParams params = new SetupIntentCreateParams.Builder()
                .setCustomer(customer.getId())
                .build();
            SetupIntent setupIntent = SetupIntent.create(params);

            JsonObject json = new JsonObject();
            json.addProperty("publishableKey", dotenv.get("STRIPE_PUBLISHABLE_KEY"));
            json.addProperty("clientSecret", setupIntent.getClientSecret());
            return json;
        });

        post("/webhook", (request, response) -> {
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");
            String endpointSecret = dotenv.get("STRIPE_WEBHOOK_SECRET");

            Event event = null;

            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                // Invalid signature
                response.status(400);
                return "";
            }

            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

            switch (event.getType()) {
            case "setup_intent.created":
                System.out.println("🔔 A new SetupIntent was created.");
                break;
            case "setup_intent.succeeded":
                System.out.println("🔔 A SetupIntent has successfully set up a PaymentMethod.");
                break;
            case "setup_intent.setup_failed":
                System.out.println("🔔 A SetupIntent has failed the attempt to set up a PaymentMethod.");
                break;
            case "payment_method.attached":
                PaymentMethod paymentMethod = ApiResource.GSON.fromJson(deserializer.getRawJson(), PaymentMethod.class);

                // At this point, associate the ID of the Customer object with your
                // own internal representation of a customer, if you have one.
                Customer customer = Customer.retrieve(paymentMethod.getCustomer());

                System.out.println("🔔 A PaymentMethod has successfully been saved to a Customer.");

                // Optional: update the Customer billing information with billing details from the PaymentMethod
                Map<String, Object> params = new HashMap<>();
                params.put("email", paymentMethod.getBillingDetails().getEmail());
                customer.update(params);
                System.out.println("🔔 Customer successfully updated.");

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