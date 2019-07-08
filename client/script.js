let stripeElements = function(setupIntent) {
  var stripe = Stripe("pk_test_4wpDbRpfgtxLW0JHXwHPnC7n00M0KnKoYv");
  var elements = stripe.elements();

  // Element styles
  var style = {
    base: {
      fontSize: "16px",
      color: "#32325d",
      fontFamily:
        "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif",
      fontSmoothing: "antialiased",
      "::placeholder": {
        color: "rgba(0,0,0,0.4)"
      }
    }
  };

  var card = elements.create("card", { style: style });

  card.mount("#card-element");

  // Element focus ring
  card.on("focus", function() {
    var el = document.getElementById("card-element");
    el.classList.add("focused");
  });

  card.on("blur", function() {
    var el = document.getElementById("card-element");
    el.classList.remove("focused");
  });

  // Handle payment submission when user clicks the pay button.
  var form = document.getElementById("payment-form");
  form.addEventListener("submit", function(event) {
    event.preventDefault();
    stripe
      .handleCardSetup(setupIntent.client_secret, card, {
        payment_method_data: {}
      })
      .then(function(result) {
        if (result.error) {
          var displayError = document.getElementById("card-errors");
          displayError.textContent = result.error.message;
        } else {
          console.log(result);
          stripePaymentHandler();
        }
      });
  });
};

function getSetupIntent() {
  return fetch("/create-setup-intent", {
    method: "post",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify()
  })
    .then(function(response) {
      return response.json();
    })
    .then(function(setupIntent) {
      console.log(setupIntent);
      document
        .getElementById("card-button")
        .setAttribute("data-secret", setupIntent.client_secret);
      stripeElements(setupIntent);
    });
}

getSetupIntent();

// Implement logic to handle the users authorization for payment.
// Here you will want to redirect to a successful payments page, or update the page.
function stripePaymentHandler() {
  //   window.location.replace("/endstate.html");
}
