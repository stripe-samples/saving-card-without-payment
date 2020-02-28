<?php
use Slim\Http\Request;
use Slim\Http\Response;
use Stripe\Stripe;

require 'vendor/autoload.php';

$dotenv = Dotenv\Dotenv::create(__DIR__);
$dotenv->load();

require './config.php';

$app = new \Slim\App;

// Instantiate the logger as a dependency
$container = $app->getContainer();
$container['logger'] = function ($c) {
  $settings = $c->get('settings')['logger'];
  $logger = new Monolog\Logger($settings['name']);
  $logger->pushProcessor(new Monolog\Processor\UidProcessor());
  $logger->pushHandler(new Monolog\Handler\StreamHandler(__DIR__ . '/logs/app.log', \Monolog\Logger::DEBUG));
  return $logger;
};

$app->add(function ($request, $response, $next) {
    Stripe::setApiKey(getenv('STRIPE_SECRET_KEY'));
    return $next($request, $response);
});

$app->get('/', function (Request $request, Response $response, array $args) {   
  // Display checkout page
  return $response->write(file_get_contents(getenv('STATIC_DIR') . '/index.html'));
});

$app->post('/create-setup-intent', function (Request $request, Response $response, array $args) {  
    // Create or use an existing Customer to associate with the SetupIntent.
    // The PaymentMethod will be stored to this Customer for later use.
    $customer = \Stripe\Customer::create();  

    $setupIntent = \Stripe\SetupIntent::create([
      'customer' => $customer->id
    ]);
    $pub_key = getenv('STRIPE_PUBLISHABLE_KEY');

    // Send publishable key, Setup Intent details to client
    return $response->withJson(array('publishableKey' => $pub_key, 'clientSecret' => $setupIntent->client_secret));
    // return $response->withJson($setupIntent);
});


$app->post('/webhook', function(Request $request, Response $response) {
    $logger = $this->get('logger');
    $event = $request->getParsedBody();
    // Parse the message body (and check the signature if possible)
    $webhookSecret = getenv('STRIPE_WEBHOOK_SECRET');
    if ($webhookSecret) {
      try {
        $event = \Stripe\Webhook::constructEvent(
          $request->getBody(),
          $request->getHeaderLine('stripe-signature'),
          $webhookSecret
        );
      } catch (\Exception $e) {
        return $response->withJson([ 'error' => $e->getMessage() ])->withStatus(403);
      }
    } else {
      $event = $request->getParsedBody();
    }
    $type = $event['type'];
    $object = $event['data']['object'];
    
    if ($type == 'setup_intent.created') {
      $logger->info('🔔 A new SetupIntent was created. ');
    }

    if ($type == 'setup_intent.succeeded') {
      $logger->info('🔔 A SetupIntent has successfully set up a PaymentMethod for future use.'); 
    }

    if ($type == 'payment_method.attached') {
      $logger->info('🔔 A PaymentMethod has successfully been saved to a Customer.');

      // At this point, associate the ID of the Customer object with your
      // own internal representation of a customer, if you have one. 

      // Optional: update the Customer billing information with billing details from the PaymentMethod
      $customer = \Stripe\Customer::update(
        $object->customer,
        ['email' => $object->billing_details->email]
      );

      $logger->info('🔔 Customer successfully updated.');
    }

    if ($type == 'setup_intent.setup_failed') {
      $logger->info('🔔 A SetupIntent has failed the attempt to set up a PaymentMethod.');
    }

    return $response->withJson([ 'status' => 'success' ])->withStatus(200);
});

$app->run();