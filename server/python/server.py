#! /usr/bin/env python3.6

"""
server.py
Stripe Recipe.
Python 3.6 or newer required.
"""

import stripe
import json
import os

from flask import Flask, render_template, jsonify, request, send_from_directory
from dotenv import load_dotenv, find_dotenv

# Setup Stripe python client library
load_dotenv(find_dotenv())
stripe.api_key = os.getenv('STRIPE_SECRET_KEY')
stripe.api_version = os.getenv('STRIPE_API_VERSION')

static_dir = str(os.path.abspath(os.path.join(
    __file__, "..", os.getenv("STATIC_DIR"))))
app = Flask(__name__, static_folder=static_dir,
            static_url_path="", template_folder=static_dir)


@app.route('/', methods=['GET'])
def get_setup_intent_page():
    return render_template('index.html')


@app.route('/public-key', methods=['GET'])
def get_publishable_key():
    return jsonify(publicKey=os.getenv('STRIPE_PUBLISHABLE_KEY'))


@app.route('/create-setup-intent', methods=['POST'])
def create_setup_intent():
    setup_intent = stripe.SetupIntent.create()
    return jsonify(setup_intent)


@app.route('/webhook', methods=['POST'])
def webhook_received():
    # You can use webhooks to receive information about asynchronous payment events.
    # For more about our webhook events check out https://stripe.com/docs/webhooks.
    webhook_secret = os.getenv('STRIPE_WEBHOOK_SECRET')
    request_data = json.loads(request.data)

    if webhook_secret:
        # Retrieve the event by verifying the signature using the raw body and secret if webhook signing is configured.
        signature = request.headers.get('stripe-signature')
        try:
            event = stripe.Webhook.construct_event(
                payload=request.data, sig_header=signature, secret=webhook_secret)
            data = event['data']
        except Exception as e:
            return e
        # Get the type of webhook event sent - used to check the status of PaymentIntents.
        event_type = event['type']
    else:
        data = request_data['data']
        event_type = request_data['type']
    data_object = data['object']

    if event_type == 'setup_intent.created':
        print('🔔 A new SetupIntent was created.')

    if event_type == 'setup_intent.succeeded':
        print('🔔 A SetupIntent has successfully setup a PaymentMethod for future use.')

        # Get Customer billing details from the PaymentMethod
        payment_method = stripe.PaymentMethod.retrieve(
            data_object['payment_method'])

        # This creates a new Customer and attaches the PaymentMethod in one API call.
        customer = stripe.Customer.create(
            payment_method=data_object['payment_method'],
            email=payment_method['billing_details']['email'])
            
        # At this point, associate the ID of the Customer object with your
        # own internal representation of a customer, if you have one.

        print('🔔 A Customer has successfully been created.')

        # You can also attach a PaymentMethod to an existing Customer
        # https://stripe.com/docs/api/payment_methods/attach

    if event_type == 'setup_intent.setup_failed':
        print(
            '🔔A SetupIntent has failed the attempt to setup a PaymentMethod.')

    return jsonify({'status': 'success'})


if __name__ == '__main__':
    app.run(host="localhost", port=4242, debug=True)
