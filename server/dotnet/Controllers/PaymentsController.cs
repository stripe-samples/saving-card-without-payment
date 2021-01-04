using System;
using System.IO;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using Stripe;

namespace server.Controllers
{
    public class PaymentsController : Controller
    {
        public readonly IOptions<StripeOptions> options;
        private readonly IStripeClient client;

        public PaymentsController(IOptions<StripeOptions> options)
        {
            this.options = options;
            this.client = new StripeClient(this.options.Value.SecretKey);
            StripeConfiguration.StripeClient = this.client;
        }

        [HttpGet("public-key")]
        public PublicKeyResponse PublicKey()
        {
          // Fetches the public key so that the front end can
          // initialize Stripe.js which is required for confirming
          // the SetupIntent on the client.
          return new PublicKeyResponse
          {
            PublicKey = this.options.Value.PublishableKey,
          };
        }

        [HttpPost("create-setup-intent")]
        public async Task<SetupIntent> CreateSetupIntent()
        {
          // Here we create a customer, then create a SetupIntent.
          // The SetupIntent is the object that keeps track of the
          // Customer's intent to allow saving their card details
          // so they can be charged later when the customer is no longer
          // on your site.

          // Create a customer
          var options = new CustomerCreateOptions();
          var service = new CustomerService();
          var customer = await service.CreateAsync(options);

          // Create a setup intent, and return the setup intent.
          var setupIntentOptions = new SetupIntentCreateOptions
          {
            Customer = customer.Id,
          };
          var setupIntentService = new SetupIntentService();

          // We're returning the full SetupIntent object here, but you could
          // also just return the ClientSecret for the newly created
          // SetupIntent as that's the only required data to confirm the
          // SetupIntent on the front end with Stripe.js.
          return await setupIntentService.CreateAsync(setupIntentOptions);
        }

        [HttpPost("webhook")]
        public async Task<IActionResult> Webhook()
        {
            var json = await new StreamReader(HttpContext.Request.Body).ReadToEndAsync();
            Event stripeEvent;
            try
            {
                stripeEvent = EventUtility.ConstructEvent(
                    json,
                    Request.Headers["Stripe-Signature"],
                    this.options.Value.WebhookSecret
                );
                Console.WriteLine($"Webhook notification with type: {stripeEvent.Type} found for {stripeEvent.Id}");
            }
            catch(Exception e)
            {
                Console.WriteLine($"Something failed {e}");
                return BadRequest();
            }

            if(stripeEvent.Type == "checkout.session.completed")
            {
                var session = stripeEvent.Data.Object as Stripe.Checkout.Session;
                Console.WriteLine($"Session ID: {session.Id}");
                // Take some action based on session.
            }

            return Ok();
        }
    }
}
