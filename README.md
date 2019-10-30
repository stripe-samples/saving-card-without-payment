# Saving cards without payment sample

This sample shows how to build a form to save a credit card without taking a payment using the [Setup Intents API](https://stripe.com/docs/api/setup_intents). This sample
is a fully working version of [this guide](https://stripe.com/docs/payments/cards/saving-cards#saving-card-without-payment).

**Demo**

See a hosted version of the [sample](https://q0sh7.sse.codesandbox.io/) in test mode or [fork on codesandbox.io](https://codesandbox.io/s/stripe-saving-card-without-payment-q0sh7)

The hosted demo is running in test mode -- use `4242424242424242` as a test card number with any CVC + future expiration date.

Use the `4000000000003220` test card number to trigger a 3D Secure challenge flow.

Read more about test cards on Stripe at https://stripe.com/docs/testing.

<img src="./saving-card-details.gif" alt="Saving a card to a customer demo" align="center">

## How to run locally

This sample includes 5 server implementations in Node, Ruby, Python, Java, and PHP. 

Follow the steps below to run locally.

**1. Clone the repository:**

```
git clone https://github.com/stripe-samples/saving-card-without-payment
```

**2. Copy the .env.example to a .env file:**

Copy the .env.example file into a file named .env in the folder of the server you want to use. For example:

```
cp .env.example server/node/.env
```

You will need a Stripe account in order to run the demo. Once you set up your account, go to the Stripe [developer dashboard](https://stripe.com/docs/development#api-keys) to find your API keys.

```
STRIPE_PUBLISHABLE_KEY=<replace-with-your-publishable-key>
STRIPE_SECRET_KEY=<replace-with-your-secret-key>
```

`CLIENT_DIR` tells the server where to the client files are located and does not need to be modified unless you move the server files.

**3. Follow the server instructions on how to run:**

Pick the server language you want and follow the instructions in the server folder README on how to run.

```
cd server/node # there's a README in this folder with instructions
npm install
npm start
```

**4. [Optional] Run a webhook locally:**

If you want to test with a local webhook on your machine, you can use the Stripe CLI to easily spin one up.

First [install the CLI](https://stripe.com/docs/stripe-cli) and [link your Stripe account](https://stripe.com/docs/stripe-cli#link-account).

```
stripe listen --forward-to localhost:4242/webhook
```

The CLI will print a webhook secret key to the console. Set `STRIPE_WEBHOOK_SECRET` to this value in your .env file.

You should see events logged in the console where the CLI is running.

When you are ready to create a live webhook endpoint, follow our guide in the docs on [configuring a webhook endpoint in the dashboard](https://stripe.com/docs/webhooks/setup#configure-webhook-settings). 

### Supported languages

- [JavaScript (Node)](/server/node)
- [Python (Flask)](/server/python)
- [Ruby (Sinatra)](/server/ruby)
- [PHP (Slim)](/server/php)
- [Java (Spark)](/server/java)

## FAQ

Q: Why did you pick these frameworks?

A: We chose the most minimal framework to convey the key Stripe calls and concepts you need to understand. These demos are meant as an educational tool that helps you roadmap how to integrate Stripe within your own system independent of the framework.

Q: Can you show me how to build X?

A: We are always looking for new recipe ideas, please email dev-samples@stripe.com with your suggestion!

## Author(s)

[@ctrudeau-stripe](https://twitter.com/trudeaucj)
