// Strategy Design Pattern is a powerful tool that enables software engineers 
// to achieve just that by allowing different algorithms or behaviors to be selected dynamically at runtime

// Problem

public class PaymentProcessor {
    // This method processes payment based on the payment method type
    public void processPayment(String paymentMethod) {
        if (paymentMethod.equals("CreditCard")) {
            CreditCardPayment creditCard = new CreditCardPayment();
            creditCard.processPayment(); // Process Credit Card payment
        } else if (paymentMethod.equals("PayPal")) {
            PayPalPayment payPal = new PayPalPayment();
            payPal.processPayment(); // Process PayPal payment
        } else if (paymentMethod.equals("Crypto")) {
            CryptoPayment crypto = new CryptoPayment();
            crypto.processPayment(); // Process Crypto payment
        } else if (paymentMethod.equals("Stripe")) {
            StripePayment stripe = new StripePayment();
            stripe.processPayment(); // Process Stripe payment
        } else {
            System.out.println("Payment method not supported.");
        }
    }
}

// Solution

// PaymentStrategy interface (defines the common method for all payment types)
public interface PaymentStrategy {
    void processPayment(); // Abstract method for processing payments
}

public class CreditCardPayment implements PaymentStrategy {
  public void processPayment() {
    System.out.println("Processing credit card payment...");
  }
}

public class PayPalPayment implements PaymentStrategy {
  public void processPayment() {
    System.out.println("Processing PayPal payment...");
  }
}

public class CryptoPayment implements PaymentStrategy {
  public void processPayment() {
    System.out.println("Processing crypto payment...");
  }
}

public class StripePayment implements PaymentStrategy {
  public void processPayment() {
    System.out.println("Processing Stripe payment...");
  }
}

public class PaymentProcessor {
  private PaymentStrategy paymentStrategy; // Reference to a payment strategy
  // Constructor to set the payment strategy
  public PaymentProcessor(PaymentStrategy paymentStrategy) {
    this.paymentStrategy = paymentStrategy;
  }

  // Process payment using the current strategy
  public void processPayment() {
    paymentStrategy
        .processPayment(); // Delegate the payment processing to the strategy
  }

  // Dynamically change payment strategy at runtime
  public void setPaymentStrategy(PaymentStrategy paymentStrategy) {
    this.paymentStrategy = paymentStrategy;
  }
}

public class Main {
  public static void main(String[] args) {
    // Create strategy instances for each payment type
    PaymentStrategy creditCard = new CreditCardPayment();
    PaymentStrategy payPal = new PayPalPayment();
    PaymentStrategy crypto = new CryptoPayment();
    PaymentStrategy stripe = new StripePayment();
    // Use the Strategy Pattern to process payments
    PaymentProcessor processor =
        new PaymentProcessor(creditCard); // Initially using CreditCardPayment
    processor.processPayment(); // Processing credit card payment...
    // Dynamically change the payment strategy to PayPal
    processor.setPaymentStrategy(payPal);
    processor.processPayment(); // Processing PayPal payment...
    // Switch to Crypto
    processor.setPaymentStrategy(crypto);
    processor.processPayment(); // Processing crypto payment...
    // Switch to Stripe
    processor.setPaymentStrategy(stripe);
    processor.processPayment(); // Processing Stripe payment...
  }
}