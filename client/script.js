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
      .handleCardSetup(setupIntent.client_secret, card)
      .then(function(result) {
        if (result.error) {
          var displayError = document.getElementById("card-errors");
          displayError.textContent = result.error.message;
        } else {
          console.log(result);
          fetch("/create-customer", {
            method: "post",
            headers: {
              "Content-Type": "application/json"
            },
            body: JSON.stringify(result.setupIntent)
          })
            .then(function(response) {
              return response.json();
            })
            .then(function(customer) {
              console.log(customer);
              document.getElementById("endstate").style.display = "block";
              document.getElementById("startstate").style.display = "none";
              document.getElementById(
                "setupIntent-response"
              ).innerHTML = JSON.stringify(result.setupIntent, null, 2);
            });
        }
      });
  });
};

function getSetupIntent() {
  return fetch("/create-setup-intent", {
    method: "get",
    headers: {
      "Content-Type": "application/json"
    }
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
