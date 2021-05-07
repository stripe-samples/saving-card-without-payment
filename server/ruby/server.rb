require 'stripe'
require 'sinatra'
require 'dotenv'
require './config_helper.rb'

# Copy the .env.example in the root into a .env file in this folder

Dotenv.load
ConfigHelper.check_env!
# For sample support and debugging, not required for production:
Stripe.set_app_info(
  'stripe-samples/saving-card-without-payment',
  version: '0.0.1',
  url: 'https://github.com/stripe-samples/saving-card-without-payment'
)
Stripe.api_version = '2020-08-27'
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

set :static, true
set :public_folder, File.join(File.dirname(__FILE__), ENV['STATIC_DIR'])
set :port, 4242

get '/' do
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

get '/public-key' do
  content_type 'application/json'

  {
    'publicKey': ENV['STRIPE_PUBLISHABLE_KEY']
  }.to_json
end

post '/create-setup-intent' do
  content_type 'application/json'

  customer = Stripe::Customer.create

  data = Stripe::SetupIntent.create(
    customer: customer['id']
  )
  data.to_json
end

post '/webhook' do
  # You can use webhooks to receive information about asynchronous payment events.
  # For more about our webhook events check out https://stripe.com/docs/webhooks.
  webhook_secret = ENV['STRIPE_WEBHOOK_SECRET']
  payload = request.body.read
  if !webhook_secret.empty?
    # Retrieve the event by verifying the signature using the raw body and secret if webhook signing is configured.
    sig_header = request.env['HTTP_STRIPE_SIGNATURE']
    event = nil

    begin
      event = Stripe::Webhook.construct_event(
        payload, sig_header, webhook_secret
      )
    rescue JSON::ParserError => e
      # Invalid payload
      status 400
      return
    rescue Stripe::SignatureVerificationError => e
      # Invalid signature
      puts '⚠️  Webhook signature verification failed.'
      status 400
      return
    end
  else
    data = JSON.parse(payload, symbolize_names: true)
    event = Stripe::Event.construct_from(data)
  end
  # Get the type of webhook event sent - used to check the status of SetupIntents.
  event_type = event['type']
  data = event['data']
  data_object = data['object']

  if event_type == 'setup_intent.created'
    puts '🔔 A new SetupIntent was created.'
  end

  if event_type == 'setup_intent.setup_failed'
    puts '🔔  A SetupIntent has failed the attempt to set up a PaymentMethod.'
  end

  if event_type == 'setup_intent.succeeded'
    puts '🔔 A SetupIntent has successfully set up a PaymentMethod for future use.'
  end

  if event_type == 'payment_method.attached'
    puts '🔔 A PaymentMethod has successfully been saved to a Customer.'

    # At this point, associate the ID of the Customer object with your
    # own internal representation of a customer, if you have one.

    # Optional: update the Customer billing information with billing details from the PaymentMethod
    customer = Stripe::Customer.update(
      data_object['customer'],
      email: data_object['billing_details']['email']
    )

    puts "🔔 Customer #{customer['id']} successfully updated."

    # You can also attach a PaymentMethod to an existing Customer
    # https://stripe.com/docs/api/payment_methods/attach
  end

  content_type 'application/json'
  {
    status: 'success'
  }.to_json
end
