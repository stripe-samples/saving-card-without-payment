//
//  CheckoutViewController.swift
//  app
//
//  Created by Yuki Tokuhiro on 9/25/19.
//  Copyright © 2019 stripe-samples. All rights reserved.
//

import UIKit
import Stripe

/**
 * To run this app, you'll need to first run the sample server locally.
 * Follow the "How to run locally" instructions in the root directory's README.md to get started.
 * Once you've started the server, open http://localhost:4242 in your browser to check that the
 * server is running locally.
 * After verifying the sample server is running locally, build and run the app using the iOS simulator.
 */
let BackendUrl = "http://127.0.0.1:4242/"

class CheckoutViewController: UIViewController {
    var setupIntentClientSecret: String?

    lazy var cardTextField: STPPaymentCardTextField = {
        let cardTextField = STPPaymentCardTextField()
        return cardTextField
    }()
    lazy var payButton: UIButton = {
        let button = UIButton(type: .custom)
        button.layer.cornerRadius = 5
        button.backgroundColor = .systemBlue
        button.titleLabel?.font = UIFont.systemFont(ofSize: 22)
        button.setTitle("Save", for: .normal)
        button.addTarget(self, action: #selector(pay), for: .touchUpInside)
        return button
    }()
    lazy var emailTextField: UITextField = {
        let emailTextField = UITextField()
        emailTextField.placeholder = "Enter your email"
        emailTextField.borderStyle = .roundedRect
        return emailTextField
    }()
    lazy var mandateLabel: UILabel = {
        let mandateLabel = UILabel()
        // Collect permission to reuse the customer's card:
        // In your app, add terms on how you plan to process payments and
        // reference the terms of the payment in the checkout flow
        // See https://stripe.com/docs/strong-customer-authentication/faqs#mandates
        mandateLabel.text = "I authorise Stripe Samples to send instructions to the financial institution that issued my card to take payments from my card account in accordance with the terms of my agreement with you."
        mandateLabel.numberOfLines = 0
        mandateLabel.font = UIFont.preferredFont(forTextStyle: .footnote)
        mandateLabel.textColor = .systemGray
        return mandateLabel
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        let stackView = UIStackView(arrangedSubviews: [emailTextField, cardTextField, payButton, mandateLabel])
        stackView.axis = .vertical
        stackView.spacing = 20
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)
        NSLayoutConstraint.activate([
            stackView.leftAnchor.constraint(equalToSystemSpacingAfter: view.leftAnchor, multiplier: 2),
            view.rightAnchor.constraint(equalToSystemSpacingAfter: stackView.rightAnchor, multiplier: 2),
            stackView.topAnchor.constraint(equalToSystemSpacingBelow: view.topAnchor, multiplier: 2),
        ])
        startCheckout()
    }

    func displayAlert(title: String, message: String, restartDemo: Bool = false) {
        DispatchQueue.main.async {
            let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
            if restartDemo {
                alert.addAction(UIAlertAction(title: "Restart demo", style: .cancel) { _ in
                    self.cardTextField.clear()
                    self.emailTextField.text = nil
                    self.startCheckout()
                })
            }
            else {
                alert.addAction(UIAlertAction(title: "OK", style: .cancel))
            }
            self.present(alert, animated: true, completion: nil)
        }
    }

    func startCheckout() {
        // Create a SetupIntent by calling the sample server's /create-setup-intent endpoint.
        let url = URL(string: BackendUrl + "create-setup-intent")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let task = URLSession.shared.dataTask(with: request, completionHandler: { [weak self] (data, response, error) in
            guard let response = response as? HTTPURLResponse,
                response.statusCode == 200,
                let data = data,
                let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String : Any],
                let clientSecret = json["clientSecret"] as? String,
                let stripePublishableKey = json["publishableKey"] as? String else {
                    let message = error?.localizedDescription ?? "Failed to decode response from server."
                    self?.displayAlert(title: "Error loading page", message: message)
                    return
            }
            self?.setupIntentClientSecret = clientSecret
            // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
            Stripe.setDefaultPublishableKey(stripePublishableKey)
        })
        task.resume()
    }

    @objc
    func pay() {
        guard let setupIntentClientSecret = setupIntentClientSecret else {
            return;
        }
        // Collect card details
        let cardParams = cardTextField.cardParams
        
        // Collect the customer's email to know which customer the PaymentMethod belongs to.
        let billingDetails = STPPaymentMethodBillingDetails()
        billingDetails.email = emailTextField.text
        
        // Create SetupIntent confirm parameters with the above
        let paymentMethodParams = STPPaymentMethodParams(card: cardParams, billingDetails: billingDetails, metadata: nil)
        let setupIntentParams = STPSetupIntentConfirmParams(clientSecret: setupIntentClientSecret)
        setupIntentParams.paymentMethodParams = paymentMethodParams

        // Complete the setup
        let paymentHandler = STPPaymentHandler.shared()
        paymentHandler.confirmSetupIntent(withParams: setupIntentParams, authenticationContext: self) { status, setupIntent, error in
            switch (status) {
            case .failed:
                self.displayAlert(title: "Setup failed", message: error?.localizedDescription ?? "")
                break
            case .canceled:
                self.displayAlert(title: "Setup canceled", message: error?.localizedDescription ?? "")
                break
            case .succeeded:
                self.displayAlert(title: "Setup succeeded", message: setupIntent?.description ?? "", restartDemo: true)
                break
            @unknown default:
                fatalError()
                break
            }
        }
    }
}

extension CheckoutViewController: STPAuthenticationContext {
    func authenticationPresentingViewController() -> UIViewController {
        return self
    }
}

