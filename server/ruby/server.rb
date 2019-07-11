require 'stripe'
require 'sinatra'
require 'dotenv'

Dotenv.load(File.dirname(__FILE__) + '/../../.env')
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

set :static, true
set :public_folder, File.join(File.dirname(__FILE__), '../../client/')
set :port, 4242

get '/' do
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

get '/create-setup-intent' do
  content_type 'application/json'

  data = Stripe::SetupIntent.create
  data.to_json
end

post '/create-customer' do
  content_type 'application/json'
  data = JSON.parse request.body.read

  # This creates a new Customer and attaches the PaymentMethod in one API call.
  customer = Stripe::Customer.create(payment_method: data['payment_method'])

  customer.to_json
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
      puts "⚠️  Webhook signature verification failed."
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

  if event_type == 'setup_intent.setup_failed'
    puts "🔔  Webhook received!"
  end

  if event_type == 'setup_intent.succeeded'
    puts "🔔  Webhook received!"
  end

  if event_type == 'setup_intent.created'
    puts "🔔  Webhook received!"
  end

  content_type 'application/json'
  {
    status: 'success'
  }.to_json

end
