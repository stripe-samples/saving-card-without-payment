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

@app.route('/create-setup-intent', methods=['POST'])
def create_setup_intent():
    # Create or use an existing Customer to associate with the SetupIntent.
    # The PaymentMethod will be stored to this Customer for later use.
    customer = stripe.Customer.create()

    setup_intent = stripe.SetupIntent.create(
        customer=customer['id']
    )
    return jsonify(publishableKey=os.getenv('STRIPE_PUBLISHABLE_KEY'), clientSecret=setup_intent.client_secret)


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
        print(
            '🔔 A SetupIntent has successfully set up a PaymentMethod for future use.')
    
    if event_type == 'payment_method.attached':
        print('🔔 A PaymentMethod has successfully been saved to a Customer.')

        # At this point, associate the ID of the Customer object with your
        # own internal representation of a customer, if you have one.

        # Optional: update the Customer billing information with billing details from the PaymentMethod
        stripe.Customer.modify(
            data_object['customer'],
            email=data_object['billing_details']['email']
        )
        print('🔔 Customer successfully updated.')

    if event_type == 'setup_intent.setup_failed':
        print(
            '🔔 A SetupIntent has failed the attempt to set up a PaymentMethod.')

    return jsonify({'status': 'success'})


if __name__ == '__main__':
    app.run(host="localhost", port=4242, debug=True)
