<?php
use Slim\Http\Request;
use Slim\Http\Response;
use Stripe\Stripe;

require 'vendor/autoload.php';

if (PHP_SAPI == 'cli-server') {
  $_SERVER['SCRIPT_NAME'] = '/index.php';
}

$dotenv = Dotenv\Dotenv::create(realpath('../..'));
$dotenv->load();

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
$app->get('/css/normalize.css', function (Request $request, Response $response, array $args) { 
  return $response->withHeader('Content-Type', 'text/css')->write(file_get_contents('../../client/css/normalize.css'));
});
$app->get('/css/global.css', function (Request $request, Response $response, array $args) { 
  return $response->withHeader('Content-Type', 'text/css')->write(file_get_contents('../../client/css/global.css'));
});
$app->get('/script.js', function (Request $request, Response $response, array $args) { 
  return $response->withHeader('Content-Type', 'text/javascript')->write(file_get_contents('../../client/script.js'));
});

$app->get('/pasha-card.svg', function (Request $request, Response $response, array $args) { 
  return $response->withHeader('Content-Type', 'image/svg+xml')->write(file_get_contents('../../client/pasha-card.svg'));
});


$app->get('/', function (Request $request, Response $response, array $args) {   
  // Display checkout page
  return $response->write(file_get_contents('../../client/index.html'));
});

$app->get('/public-key', function (Request $request, Response $response, array $args) {
  $pub_key = getenv('STRIPE_PUBLIC_KEY');
  
  // Send public key details to client
  return $response->withJson(array('publicKey' => $pub_key));
});


$app->post('/create-setup-intent', function (Request $request, Response $response, array $args) {  
    $setupIntent = \Stripe\SetupIntent::create();
    // Send Setup Intent details to client
    return $response->withJson($setupIntent);
});

$app->post('/create-customer', function (Request $request, Response $response, array $args) {  
  $body = json_decode($request->getBody());
  
  # This creates a new Customer and attaches the PaymentMethod in one API call.
  # At this point, associate the ID of the Customer object with your
  # own internal representation of a customer, if you have one. 
  $customer = \Stripe\Customer::create([
    "payment_method" => $body->payment_method
  ]);


  return $response->withJson($customer);
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
      $logger->info('Occurs when a new SetupIntent is created. ');
    }

    if ($type == 'setup_intent.succeeded') {
      $logger->info('Occurs when an SetupIntent has successfully setup a payment method.');
    }

    if ($type == 'setup_intent.failed') {
      $logger->info('Occurs when a SetupIntent has failed the attempt to setup a payment method.');
    }

    return $response->withJson([ 'status' => 'success' ])->withStatus(200);
});

$app->run();