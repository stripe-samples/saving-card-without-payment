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
import com.stripe.param.CustomerCreateParams;
import com.stripe.model.EventDataObjectDeserializer;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();

    public static void main(String[] args) {
        port(4242);
        String ENV_PATH = "../../";
        Dotenv dotenv = Dotenv.configure().directory(ENV_PATH).load();

        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        get("/public-key", (request, response) -> {
            response.type("application/json");
            JsonObject publicKey = new JsonObject();
            publicKey.addProperty("publicKey", dotenv.get("STRIPE_PUBLIC_KEY"));
            return publicKey.toString();
        });

        post("/create-setup-intent", (request, response) -> {
            response.type("application/json");

            Map<String, Object> params = new HashMap<>();
            SetupIntent setupIntent = SetupIntent.create(params);

            return gson.toJson(setupIntent);
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

            switch (event.getType()) {
            case "setup_intent.created":
                System.out.println("🔔 A new SetupIntent was created.");
                break;
            case "setup_intent.succeeded":
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                SetupIntent intent = ApiResource.GSON.fromJson(deserializer.getRawJson(), SetupIntent.class);
                System.out.println("🔔 A SetupIntent has successfully setup a PaymentMethod.");

                // Get Customer billing details from the PaymentMethod
                PaymentMethod paymentMethod = PaymentMethod.retrieve(intent.getPaymentMethod());

                // Create a Customer to store the PaymentMethod ID for later use
                Customer customer = Customer
                        .create(new CustomerCreateParams.Builder().setPaymentMethod(intent.getPaymentMethod())
                                .setEmail(paymentMethod.getBillingDetails().getEmail()).build());

                // At this point, associate the ID of the Customer object with your
                // own internal representation of a customer, if you have one.
                
                System.out.println("🔔 A Customer has successfully been created.");

                // You can also attach a PaymentMethod to an existing Customer
                // https://stripe.com/docs/api/payment_methods/attach
                break;
            case "setup_intent.setup_failed":
                System.out.println("🔔 A SetupIntent has failed the attempt to setup a PaymentMethod.");
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