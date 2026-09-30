/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * <h1>PaymentSimulation</h1>
 * <p>POS terminallerinden gelen ödeme trafiği. Her istek farklı Idempotency-Key ile imzalanır; kartlar farklı
 * bankalara dağılır (YKB, Garanti, QNB, İş Bankası, Akbank).</p>
 * <p>Parametreler: {@code -DbaseUrl=http://localhost:8081 -Drps=100 -Dduration=60 -Dterminals=20}</p>
 * <p>Ölçülen: API'nin cevap süresi (202). Uçtan uca süre (banka sonucuna kadar) Grafana'da
 * {@code payment_end_to_end_seconds} metriğinden izlenir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-8
 */
public class PaymentSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8081");
    private static final int RPS = Integer.getInteger("rps", 100);
    private static final int DURATION = Integer.getInteger("duration", 60);
    private static final int TERMINALS = Integer.getInteger("terminals", 20);
    private static final String PATH = "/v1/payments";
    private static final List<String> CARDS = List.of("5400617020092306", "4111111111111111", "5555555555554444",
            "4242424242424242", "5200828282828210");
    private static final Pattern SECRET = Pattern.compile("\"secret\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Gerçekte her terminalin ayrı secret'ı vardır; yük birden fazla terminal ve üye işyerine yayılır.
     */
    private static final List<String[]> terminals = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Iterator<Map<String, Object>> signedRequests = Stream.generate(() -> {
        String[] terminal = terminals.get(ThreadLocalRandom.current().nextInt(terminals.size()));
        String key = UUID.randomUUID().toString();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        // Tam sayı tutar: simülatörde küsuratın son iki hanesi red kodunu belirliyor (.51, .05, .54)
        int amount = ThreadLocalRandom.current().nextInt(10, 5000);
        int installment = ThreadLocalRandom.current().nextInt(10) < 8 ? 1 : 3;
        String card = CARDS.get(ThreadLocalRandom.current().nextInt(CARDS.size()));
        String body = """
                {"amount":%d.00,"currency":"TRY","installmentCount":%d,"cardNumber":"%s","expiryMonth":"12","expiryYear":"28","cvv":"000"}"""
                .formatted(amount, installment, card);
        Map<String, Object> values = new HashMap<>();
        values.put("terminalId", terminal[0]);
        values.put("key", key);
        values.put("timestamp", timestamp);
        values.put("body", body);
        values.put("signature", PosRequestSigner.sign(terminal[1], "POST", PATH, timestamp, key, body));
        return values;
    }).iterator();

    private final HttpProtocolBuilder protocol = http.baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    private final ScenarioBuilder pos = scenario("POS ödeme")
            .feed(signedRequests)
            .exec(http("POST /v1/payments")
                    .post(PATH)
                    .header("X-Terminal-Id", "#{terminalId}")
                    .header("X-Timestamp", "#{timestamp}")
                    .header("X-Signature", "#{signature}")
                    .header("Idempotency-Key", "#{key}")
                    .body(StringBody("#{body}"))
                    .check(status().is(202))
                    .check(jsonPath("$.object.paymentStatus").is("PENDING")));

    {
        setUp(pos.injectOpen(
                rampUsersPerSec(1).to(RPS).during(15),
                constantUsersPerSec(RPS).during(DURATION)))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(99.0).lt(500));
    }

    @Override
    public void before() {
        HttpClient client = HttpClient.newHttpClient();
        String run = String.valueOf(System.currentTimeMillis() % 100_000_000);
        for (int i = 0; i < TERMINALS; i++) {
            String suffix = run + String.format("%02d", i);
            String body = """
                    {"terminalId":"TRMLT%s","merchantId":"MRCLT%s","terminalType":"VIRTUAL"}""".formatted(suffix, suffix);
            try {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/v1/admin/terminals"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                Matcher matcher = SECRET.matcher(response.body());
                if (!matcher.find()) {
                    throw new IllegalStateException("Terminal could not be created: " + response.body());
                }
                terminals.add(new String[]{"TRMLT" + suffix, matcher.group(1)});
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        System.out.printf("Load test: %d terminals, %d rps for %ds against %s%n", TERMINALS, RPS, DURATION, BASE_URL);
    }
}
