# PayHere Demo Gateway

A Spring Boot application demonstrating how to integrate the **PayHere** payment gateway using the Checkout API and Notify webhooks.

This project provides a ready-to-use local testing environment with an interactive UI to generate checkout requests and view incoming webhooks in real-time.

## Features
* **Checkout Builder:** A UI to dynamically generate PayHere checkout requests.
* **Webhook Receiver:** An endpoint (`/api/payhere/notify`) to receive and verify PayHere Server-to-Server (S2S) notifications.
* **Real-time Event Viewer:** A section on the home page that continuously polls and displays the raw JSON payloads of the latest webhooks.
* **Signature Verification:** Built-in MD5 signature validation to ensure webhook authenticity.

## Prerequisites
* **Java 21**
* **PayHere Sandbox Account:** Register at [sandbox.payhere.lk](https://sandbox.payhere.lk/)
* **Local Tunnel:** A tool like [ngrok](https://ngrok.com/) or Cloudflared to expose your local server to the internet so PayHere can send webhooks.

## PayHere Dashboard Setup
To test webhooks locally while initiating checkouts from your browser, configure your PayHere sandbox environment as follows:

1. Log in to the PayHere Sandbox Dashboard.
2. Go to **Integrations** > **Add Domain/App**.
3. Select **Domain**.
4. Set the **Domain Name** exactly to: `localhost`
5. Copy the generated **Merchant ID** and **Merchant Secret**.

## Configuration

This application relies on Environment Variables for configuration. 

You need to set the following variables before running the app. Replace `<your-tunnel-url>` with your active ngrok/cloudflared HTTPS URL:

```env
PAYHERE_MERCHANT_ID=your_merchant_id
PAYHERE_MERCHANT_SECRET=your_merchant_secret
PAYHERE_NOTIFY_URL=https://<your-tunnel-url>.ngrok-free.app/api/payhere/notify

```

*Optional variables (default to localhost:8080):*

* `PAYHERE_RETURN_URL`
* `PAYHERE_CANCEL_URL`
* `PAYHERE_CURRENCY` (Default: LKR)
* `PAYHERE_ITEMS` (Default: Test Subscription)

## Running the Application

1. **Start your local tunnel:**
```bash
ngrok http 8080

```


2. **Set your environment variables** (either in your terminal, IDE Run Configuration, or an `.env` file if supported by your setup).
3. **Run the Spring Boot application using Gradle:**
```bash
./gradlew bootRun

```



## Testing the Payment Flow

> **⚠️ IMPORTANT:** Because your PayHere domain is set to `localhost`, you **must** access the web interface via `localhost`, NOT your ngrok URL. PayHere will reject the payment if the browser origin does not match.

1. Open your browser and navigate to: [http://localhost:8080/](https://www.google.com/search?q=http://localhost:8080/)
2. Fill in the optional fields in the Checkout Builder or leave them as default.
3. Click **Start Checkout**. You will be redirected to the PayHere sandbox payment page.
4. Enter one of the sandbox test cards below.
5. Complete the payment. You will be redirected back to the app's Return URL.
6. Check the **Webhook Events** section on the home page (or visit `/api/payhere/events`) to see the JSON payload successfully delivered by PayHere in the background!

## Sandbox Test Cards

For the **expiry date**, use any valid date in the future (e.g., `12/30`).
For the **CVV**, use any 3-digit number (e.g., `123`).

### Successful Payments

* **Visa:** `4916217501611292`
* **MasterCard:** `5307732125531191`
* **AMEX:** `346781005510225`

### Failed Payments (Insufficient Funds)

* **Visa:** `4024007194349121`
* **MasterCard:** `5459051433777487`
* **AMEX:** `370787711978928`

*(More failure test cards for "Limit Exceeded", "Do Not Honor", and "Network Error" can be found in the official PayHere documentation).*

## API Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/` | Home page UI with checkout builder and event viewer. |
| `GET` | `/api/payhere/checkout` | Generates the HTML auto-submit form to redirect to PayHere. |
| `POST` | `/api/payhere/notify` | The webhook endpoint PayHere calls asynchronously. |
| `GET` | `/api/payhere/events` | Returns a JSON array of the 50 most recent webhook payloads. |
| `GET` | `/api/payhere/return` | The page the user lands on after a successful payment. |
| `GET` | `/api/payhere/cancel` | The page the user lands on if they cancel the payment. |

## Tech Stack

* Java 21
* Spring Boot 4.x (WebMVC)
* Vanilla HTML/CSS/JS (Frontend)
* Gradle
