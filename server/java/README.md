# Saving cards without payment

## Requirements

- Maven
- Java

1. Build the jar

```
mvn package
```

2. Export environment variables
   (the Java sample pulls environment variables from your system)

```
export STRIPE_PUBLIC_KEY=pk_replace_with_your_key
export STRIPE_SECRET_KEY=sk_replace_with_your_key
export STRIPE_WEBHOOK_SECRET=whsec_1234
```

3. Run the packaged jar

```
java -cp target/saving-cards-without-payment-1.0.0-SNAPSHOT-jar-with-dependencies.jar com.stripe.recipe.Server
```

4. Go to `localhost:4242` in your browser to see the demo
