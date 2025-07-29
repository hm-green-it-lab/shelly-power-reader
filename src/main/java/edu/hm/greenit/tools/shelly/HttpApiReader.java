package edu.hm.greenit.tools.shelly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import org.slf4j.helpers.MessageFormatter;

import java.io.IOException;
import java.net.Authenticator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.logging.Logger;

import static edu.hm.greenit.tools.shelly.ShellyPowerReader.SHELLY_USER;
import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;
import static org.jboss.logmanager.Level.ERROR;

/**
 * IReader implementation for Shelly devices using the Common HTTP API. <br>
 * This is the standard way of communication for Generation 1 devices. <br>
 * If the device has a password set, Basic authentication is used.
 * For API documentation see: <a href="https://shelly-api-docs.shelly.cloud/gen1/#http-dialect">Shelly Gen 1 Api Docs</a>
 */
public class HttpApiReader implements IReader {

    private static final Logger LOGGER = Logger.getLogger(HttpApiReader.class.getName());
    private static final String SHELLY_HTTP_API_URI = "http://{}/status";

    private final HttpClient client;
    private final HttpRequest shellyRequest;

    public HttpApiReader(@Nonnull String shellyIp, String shellyPassword) {
        if (isNullOrEmpty(shellyPassword)) {
            client = HttpClient.newHttpClient();
        } else {
            client = HttpClient.newBuilder()
                    .authenticator(new Authenticator() {
                        @Override
                        protected java.net.PasswordAuthentication getPasswordAuthentication() {
                            return new java.net.PasswordAuthentication(SHELLY_USER, shellyPassword.toCharArray());
                        }
                    }).build();
        }
        shellyRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(MessageFormatter.format(SHELLY_HTTP_API_URI, shellyIp).getMessage()))
                .header("Accept", "application/json")
                .build();
    }

    @Override
    public Optional<Meter> readPowerConsumption() throws IOException, InterruptedException {
        // Sende die Anfrage und erhalte die Antwort
        HttpResponse<String> response = client.send(shellyRequest, HttpResponse.BodyHandlers.ofString());
        // Überprüfe den Antwortcode
        if (response.statusCode() == 200) {
            String jsonResponse = response.body();
            return Optional.ofNullable(parsePowerConsumption(jsonResponse));
        } else {
            LOGGER.log(ERROR, "Fehler beim Abrufen der Daten. HTTP-Antwortcode: " + response.statusCode());
            return Optional.empty();
        }
    }

    static Meter parsePowerConsumption(final String jsonResponse) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode metersNode = rootNode.path("meters");
        if (metersNode.isArray() && !metersNode.isEmpty()) {
            JsonNode firstMeter = metersNode.get(0);
            Meter meter = new Meter();
            meter.setPower(firstMeter.path("power").asDouble());
            meter.setTimestamp(firstMeter.path("timestamp").asLong());
            meter.setTotal(firstMeter.path("total").asLong());
            return meter;
        } else {
            LOGGER.log(ERROR, "No meters found in JSON.");
            return null;
        }
    }

}
