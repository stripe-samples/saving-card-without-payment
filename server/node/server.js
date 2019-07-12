const express = require("express");
const app = express();
const { resolve } = require("path");
const envPath = resolve("../../.env");
const env = require("dotenv").config({ path: envPath });
const stripe = require("stripe")(env.parsed.STRIPE_SECRET_KEY);

app.use(express.static("../../client"));
app.use(
  express.json({
    // We need the raw body to verify webhook signatures.
    // Let's compute it only when hitting the Stripe webhook endpoint.
    verify: function(req, res, buf) {
      if (req.originalUrl.startsWith("/webhook")) {
        req.rawBody = buf.toString();
      }
    }
  })
);

app.get("/", (req, res) => {
  const path = resolve("../../client/index.html");
  res.sendFile(path);
});

app.get("/public-key", (req, res) => {
  res.send({ publicKey: process.env.STRIPE_PUBLIC_KEY });
});

app.get("/create-setup-intent", async (req, res) => {
  res.send(await stripe.setupIntents.create());
});

app.post("/create-customer", async (req, res) => {
  // This creates a new Customer and attaches the PaymentMethod in one API call.
  const customer = await stripe.customers.create({
    payment_method: req.body.payment_method
  });
  // At this point, associate the ID of the Customer object with your
  // own internal representation of a customer, if you have one.
  console.log(customer);
  res.send(customer);
});

// Webhook handler for asynchronous events.
app.post("/webhook", async (req, res) => {
  let data;
  let eventType;
  // Check if webhook signing is configured.
  if (process.env.STRIPE_WEBHOOK_SECRET) {
    // Retrieve the event by verifying the signature using the raw body and secret.
    let event;
    let signature = req.headers["stripe-signature"];

    try {
      event = stripe.webhooks.constructEvent(
        req.rawBody,
        signature,
        process.env.STRIPE_WEBHOOK_SECRET
      );
    } catch (err) {
      console.log(`⚠️  Webhook signature verification failed.`);
      return res.sendStatus(400);
    }
    // Extract the object from the event.
    data = event.data;
    eventType = event.type;
  } else {
    // Webhook signing is recommended, but if the secret is not configured in `config.js`,
    // retrieve the event data directly from the request body.
    data = req.body.data;
    eventType = req.body.type;
  }

  if (eventType === "setup_intent.setup_failed") {
    console.log(`🔔  Webhook received! ${data} succeeded.`);
  }

  if (eventType === "setup_intent.succeeded") {
    console.log(`🔔  Webhook received! ${data} succeeded.`);
  }

  if (eventType === "setup_intent.created") {
    console.log(`🔔  Webhook received! ${data} succeeded.`);
  }

  res.sendStatus(200);
});

app.listen(4242, () => console.log(`Node server listening on port ${4242}!`));
