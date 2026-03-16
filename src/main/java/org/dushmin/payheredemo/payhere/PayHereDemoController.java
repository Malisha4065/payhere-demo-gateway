package org.dushmin.payheredemo.payhere;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/payhere")
public class PayHereDemoController {
    private static final Logger log = LoggerFactory.getLogger(PayHereDemoController.class);

    private static final int MAX_EVENTS = 50;
    private static final DateTimeFormatter ORDER_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault());

    private final PayHereProperties properties;
    private final Deque<Map<String, Object>> recentEvents = new ArrayDeque<>(MAX_EVENTS);

    public PayHereDemoController(PayHereProperties properties) {
        this.properties = properties;
    }

    @GetMapping(value = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> checkoutForm(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String currency
    ) {
        if (isBlank(properties.getMerchantId()) || isBlank(properties.getMerchantSecret())) {
            return ResponseEntity.badRequest().body(missingConfigHtml(
                    "Missing PayHere credentials. Set payhere.merchant-id and payhere.merchant-secret."
            ));
        }
        if (isBlank(properties.getNotifyUrl())) {
            return ResponseEntity.badRequest().body(missingConfigHtml(
                    "Missing notify URL. Set payhere.notify-url to your public tunnel URL (ngrok/cloudflared)."
            ));
        }

        String finalOrderId = !isBlank(orderId) ? orderId : generateOrderId();
        BigDecimal finalAmount = amount != null ? amount : new BigDecimal("1000.00");
        String finalCurrency = !isBlank(currency) ? currency : properties.getCurrency();

        if (finalAmount.signum() <= 0) {
            return ResponseEntity.badRequest().body(missingConfigHtml("Amount must be positive."));
        }

        String amountFormatted = formatAmount(finalAmount);
        String checkoutHash = checkoutHash(properties.getMerchantId(), finalOrderId, amountFormatted, finalCurrency);

        String html = """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Redirecting to PayHere...</title>
                  </head>
                  <body>
                    <h2>Redirecting to PayHere %s...</h2>
                    <p>If you are not redirected automatically, click the button.</p>
                    <form id="payhere-form" method="post" action="%s">
                      <input type="hidden" name="merchant_id" value="%s">
                      <input type="hidden" name="return_url" value="%s">
                      <input type="hidden" name="cancel_url" value="%s">
                      <input type="hidden" name="notify_url" value="%s">
                      <input type="hidden" name="first_name" value="Dushmin">
                      <input type="hidden" name="last_name" value="Malisha">
                      <input type="hidden" name="email" value="test@example.com">
                      <input type="hidden" name="phone" value="0771234567">
                      <input type="hidden" name="address" value="No.1, Galle Road">
                      <input type="hidden" name="city" value="Colombo">
                      <input type="hidden" name="country" value="Sri Lanka">
                      <input type="hidden" name="order_id" value="%s">
                      <input type="hidden" name="items" value="%s">
                      <input type="hidden" name="currency" value="%s">
                      <input type="hidden" name="amount" value="%s">
                      <input type="hidden" name="hash" value="%s">
                      <button type="submit">Continue</button>
                    </form>
                    <script>
                      document.getElementById('payhere-form').submit();
                    </script>
                  </body>
                </html>
                """.formatted(
                properties.isSandbox() ? "Sandbox" : "Live",
                htmlEscape(properties.checkoutUrl()),
                htmlEscape(properties.getMerchantId()),
                htmlEscape(properties.getReturnUrl()),
                htmlEscape(properties.getCancelUrl()),
                htmlEscape(properties.getNotifyUrl()),
                htmlEscape(finalOrderId),
                htmlEscape(properties.getItems()),
                htmlEscape(finalCurrency),
                htmlEscape(amountFormatted),
                htmlEscape(checkoutHash)
        );

        log.info("Created checkout form orderId={} amount={} currency={}", finalOrderId, amountFormatted, finalCurrency);
        return ResponseEntity.ok(html);
    }

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleNotify(@RequestParam MultiValueMap<String, String> params) {
        String merchantId = first(params, "merchant_id");
        String orderId = first(params, "order_id");
        String payhereAmount = first(params, "payhere_amount");
        String payhereCurrency = first(params, "payhere_currency");
        String statusCode = first(params, "status_code");
        String md5sig = first(params, "md5sig");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("receivedAt", Instant.now().toString());
        event.put("merchantId", merchantId);
        event.put("orderId", orderId);
        event.put("amount", payhereAmount);
        event.put("currency", payhereCurrency);
        event.put("statusCode", statusCode);
        event.put("md5sig", md5sig);

        boolean signatureOk = isValidNotifySignature(merchantId, orderId, payhereAmount, payhereCurrency, statusCode, md5sig);
        event.put("signatureValid", signatureOk);

        if (!signatureOk) {
            log.warn("Invalid PayHere signature for orderId={} statusCode={}", orderId, statusCode);
            addEvent(event);
            return ResponseEntity.badRequest().body("Invalid Signature");
        }

        if (!isBlank(properties.getMerchantId()) && !Objects.equals(properties.getMerchantId(), merchantId)) {
            log.warn("Unexpected merchant_id received: {}", merchantId);
        }

        log.info("PayHere notify verified orderId={} statusCode={} amount={} currency={}",
                orderId, statusCode, payhereAmount, payhereCurrency);
        addEvent(event);
        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> events() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", recentEvents.size());
        payload.put("events", recentEvents);
        return payload;
    }

    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public String returnUrl() {
        return statusPageHtml(
                "Payment attempt finished",
                "We will verify the payment using the notify webhook. Check the latest events to confirm status.",
                "Back to Demo",
                "/"
        );
    }

    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public String cancelUrl() {
        return statusPageHtml(
                "Payment canceled",
                "The checkout was canceled. If this was a mistake, you can restart the demo.",
                "Back to Demo",
                "/"
        );
    }

    private void addEvent(Map<String, Object> event) {
        while (recentEvents.size() >= MAX_EVENTS) {
            recentEvents.removeFirst();
        }
        recentEvents.addLast(event);
    }

    private boolean isValidNotifySignature(
            String merchantId,
            String orderId,
            String payhereAmount,
            String payhereCurrency,
            String statusCode,
            String md5sig
    ) {
        if (isBlank(merchantId) || isBlank(orderId) || isBlank(payhereAmount) || isBlank(payhereCurrency) || isBlank(statusCode) || isBlank(md5sig)) {
            return false;
        }
        String hashedSecret = md5HexUpper(properties.getMerchantSecret());
        String expected = md5HexUpper(merchantId + orderId + payhereAmount + payhereCurrency + statusCode + hashedSecret);
        return expected.equalsIgnoreCase(md5sig);
    }

    private String checkoutHash(String merchantId, String orderId, String amountFormatted, String currency) {
        String hashedSecret = md5HexUpper(properties.getMerchantSecret());
        return md5HexUpper(merchantId + orderId + amountFormatted + currency + hashedSecret);
    }

    private static String md5HexUpper(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02X", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String generateOrderId() {
        // PayHere order_id max length varies by integration; keep it short and unique for demos.
        String time = ORDER_ID_TIME.format(Instant.now());
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "DEMO_" + time + "_" + rand;
    }

    private static String first(MultiValueMap<String, String> params, String key) {
        return params.getFirst(key);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String statusPageHtml(String title, String message, String primaryLabel, String primaryHref) {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                    <link rel="stylesheet" href="/payhere.css">
                  </head>
                  <body>
                    <div class="bg"></div>
                    <main class="shell status-page">
                      <section class="card status-card">
                        <p class="kicker">PayHere Checkout</p>
                        <h1>%s</h1>
                        <p class="lede status-message">%s</p>
                        <div class="actions status-actions">
                          <a class="ghost" href="%s">%s</a>
                          <a class="ghost" href="/api/payhere/events" target="_blank" rel="noopener">View Events</a>
                        </div>
                      </section>
                    </main>
                  </body>
                </html>
                """.formatted(
                htmlEscape(title),
                htmlEscape(title),
                htmlEscape(message),
                htmlEscape(primaryHref),
                htmlEscape(primaryLabel)
        );
    }

    private static String missingConfigHtml(String message) {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>PayHere Demo Setup</title>
                  </head>
                  <body>
                    <h2>PayHere demo not configured</h2>
                    <p>%s</p>
                    <p>Configure via <code>application.yaml</code> or env vars (recommended):</p>
                    <pre>PAYHERE_MERCHANT_ID=...
PAYHERE_MERCHANT_SECRET=...
PAYHERE_NOTIFY_URL=https://&lt;your-tunnel&gt;/api/payhere/notify</pre>
                  </body>
                </html>
                """.formatted(htmlEscape(message));
    }
}
