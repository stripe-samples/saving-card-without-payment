package com.example.app

import java.io.IOException
import java.lang.ref.WeakReference

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

import com.google.gson.GsonBuilder
import org.json.JSONObject

import com.stripe.android.ApiResultCallback
import com.stripe.android.SetupIntentResult
import com.stripe.android.Stripe
import com.stripe.android.model.ConfirmSetupIntentParams
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.view.CardInputWidget


class CheckoutActivityKotlin : AppCompatActivity() {

    /**
     * To run this app, you'll need to first run the sample server locally.
     * Follow the "How to run locally" instructions in the root directory's README.md to get started.
     * Once you've started the server, open http://localhost:4242 in your browser to check that the
     * server is running locally.
     * After verifying the sample server is running locally, build and run the app using the
     * Android emulator.
     */
    // 10.0.2.2 is the Android emulator's alias to localhost
    private val backendUrl = "http://10.0.2.2:4242/"
    private val httpClient = OkHttpClient()
    private lateinit var setupIntentClientSecret: String
    private lateinit var stripe: Stripe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)
        loadPage()
    }

    private fun loadPage() {
        // Create a SetupIntent by calling the sample server's /create-setup-intent endpoint.
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = "".toRequestBody(mediaType)
        val request = Request.Builder()
            .url(backendUrl + "create-setup-intent")
            .post(body)
            .build()
        httpClient.newCall(request)
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Error: $e", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Error: $response", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val responseData = response.body?.string()
                        val json = JSONObject(responseData)

                        // The response from the server includes the Stripe publishable key and
                        // SetupIntent details.
                        val stripePublishableKey = json.getString("publishableKey")
                        setupIntentClientSecret = json.getString("clientSecret")

                        // Use the publishable key from the server to initialize the Stripe instance.
                        stripe = Stripe(applicationContext, stripePublishableKey)
                    }
                }
            })

        // Hook up the pay button to the card widget and stripe instance
        val payButton: Button = findViewById(R.id.payButton)
        payButton.setOnClickListener {
            // Collect card details
            val cardInputWidget =
                findViewById<CardInputWidget>(R.id.cardInputWidget)
            val paymentMethodCard = cardInputWidget.paymentMethodCard

            // This example collects the customer's email to know which customer the PaymentMethod belongs to, but your app might use an account id, session cookie, etc.
            val emailInput = findViewById<EditText>(R.id.emailInput)
            val billingDetails = PaymentMethod.BillingDetails.Builder()
                .setEmail((emailInput.text ?: "").toString())
                .build()

            // Create SetupIntent confirm parameters with the above
            if (paymentMethodCard != null) {
                val paymentMethodParams = PaymentMethodCreateParams
                    .create(paymentMethodCard, billingDetails, null)
                val confirmParams = ConfirmSetupIntentParams
                    .create(paymentMethodParams, setupIntentClientSecret)
                stripe.confirmSetupIntent(this, confirmParams)
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val weakActivity = WeakReference<Activity>(this)

        // Handle the result of stripe.confirmSetupIntent
        stripe.onSetupResult(requestCode, data, object : ApiResultCallback<SetupIntentResult> {
            override fun onSuccess(result: SetupIntentResult) {
                val setupIntent = result.intent
                val status = setupIntent.status
                if (status == StripeIntent.Status.Succeeded) {
                    // Setup completed successfully
                    runOnUiThread {
                        if (weakActivity.get() != null) {
                            val activity = weakActivity.get()!!
                            val builder = AlertDialog.Builder(activity)
                            builder.setTitle("Setup completed")
                            val gson = GsonBuilder().setPrettyPrinting().create()
                            builder.setMessage(gson.toJson(setupIntent))
                            builder.setPositiveButton("Restart demo") { _, _ ->
                                val cardInputWidget =
                                    findViewById<CardInputWidget>(R.id.cardInputWidget)
                                cardInputWidget.clear()
                                val emailInput = findViewById<EditText>(R.id.emailInput)
                                emailInput.text = null
                                loadPage()
                            }
                            val dialog = builder.create()
                            dialog.show()
                        }
                    }
                } else if (status == StripeIntent.Status.RequiresPaymentMethod) {
                    // Setup failed – allow retrying using a different payment method
                    runOnUiThread {
                        if (weakActivity.get() != null) {
                            val activity = weakActivity.get()!!
                            val builder = AlertDialog.Builder(activity)
                            builder.setTitle("Setup failed")
                            builder.setMessage(setupIntent.lastSetupError!!.message)
                            builder.setPositiveButton("Ok") { _, _ ->
                                val cardInputWidget =
                                    findViewById<CardInputWidget>(R.id.cardInputWidget)
                                cardInputWidget.clear()
                                val emailInput = findViewById<EditText>(R.id.emailInput)
                                emailInput.text = null
                            }
                            val dialog = builder.create()
                            dialog.show()
                        }
                    }
                }
            }

            override fun onError(e: Exception) {
                // Setup request failed – allow retrying using the same payment method
                runOnUiThread {
                    if (weakActivity.get() != null) {
                        val activity = weakActivity.get()!!
                        val builder = AlertDialog.Builder(activity)
                        builder.setMessage(e.toString())
                        builder.setPositiveButton("Ok", null)
                        val dialog = builder.create()
                        dialog.show()
                    }
                }
            }
        })
    }

}
