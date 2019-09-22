const express = require("express");
const app = express();
const { resolve } = require("path");
// Replace if using a different env file or config
const ENV_PATH = "../../.env";
const envPath = resolve(ENV_PATH);
const env = require("dotenv").config({ path: envPath });
const stripe = require("stripe")(process.env.STRIPE_SECRET_KEY);

try {
  app.use(express.static(process.env.STATIC_DIR));
} catch (e) {
  console.log("Missing env file, be sure to copy .env.example to .env");
}

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
  const path = resolve(process.env.STATIC_DIR + "/index.html");
  res.sendFile(path);
});

app.get("/public-key", (req, res) => {
  res.send({ publicKey: process.env.STRIPE_PUBLIC_KEY });
});

app.post("/create-setup-intent", async (req, res) => {
  res.send(await stripe.setupIntents.create());
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
      event = await stripe.webhooks.constructEvent(
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
    console.log(`🔔  A SetupIntent has failed the to setup a PaymentMethod.`);
  }

  if (eventType === "setup_intent.succeeded") {
    console.log(
      `🔔  A SetupIntent has successfully setup a PaymentMethod for future use.`
    );

    // Get Customer billing details from the PaymentMethod
    const paymentMethod = await stripe.paymentMethods.retrieve(
      data.object.payment_method
    );

    // Create a Customer to store the PaymentMethod ID for later use
    const customer = await stripe.customers.create({
      payment_method: data.object.payment_method,
      email: paymentMethod.billing_details.email
    });

    // At this point, associate the ID of the Customer object with your
    // own internal representation of a customer, if you have one.

    console.log(`🔔  A Customer has successfully been created ${customer.id}`);

    // You can also attach a PaymentMethod to an existing Customer
    // https://stripe.com/docs/api/payment_methods/attach
  }

  if (eventType === "setup_intent.created") {
    console.log(`🔔  A new SetupIntent is created. ${data.object.id}`);
  }

  res.sendStatus(200);
});

app.listen(4242, () => console.log(`Node server listening on port ${4242}!`));
