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

$app->get('/public-key', function (Request $request, Response $response, array $args) {
  $pub_key = getenv('STRIPE_PUBLISHABLE_KEY');
  
  // Send publishable key details to client
  return $response->withJson(array('publicKey' => $pub_key));
});

$app->post('/create-setup-intent', function (Request $request, Response $response, array $args) {  
    $setupIntent = \Stripe\SetupIntent::create();
    // Send Setup Intent details to client
    return $response->withJson($setupIntent);
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
      $logger->info('A new SetupIntent was created. ');
    }

    if ($type == 'setup_intent.succeeded') {
      $logger->info('A SetupIntent has successfully setup a PaymentMethod for future use.');

      // Get Customer billing details from the PaymentMethod
      $payment_method = \Stripe\PaymentMethod::retrieve($object->payment_method);

      // Create a Customer to store the PaymentMethod ID for later use
      $customer = \Stripe\Customer::create([
        "payment_method" => $object->payment_method,
        'email' => $payment_method->billing_details->email
      ]);    

      // At this point, associate the ID of the Customer object with your
      // own internal representation of a customer, if you have one. 
      $logger->info('A Customer has successfully been created.');

      // You can also attach a PaymentMethod to an existing Customer
      // https://stripe.com/docs/api/payment_methods/attach
    }

    if ($type == 'setup_intent.failed') {
      $logger->info('A SetupIntent has failed the attempt to setup a PaymentMethod.');
    }

    return $response->withJson([ 'status' => 'success' ])->withStatus(200);
});

$app->run();