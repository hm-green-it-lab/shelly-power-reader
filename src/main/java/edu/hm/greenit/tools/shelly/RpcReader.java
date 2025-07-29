package edu.hm.greenit.tools.shelly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Nonnull;
import org.slf4j.helpers.MessageFormatter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.hm.greenit.tools.shelly.ShellyPowerReader.SHELLY_USER;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jboss.logmanager.Level.DEBUG;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.UNAUTHORIZED;

/**
 * IReader implementation for Shelly devices using Remote Procedure Calls (RPC). <br>
 * This is the standard way of communication for Generation 2+ devices. <br>
 * If the device has a password set, RpcReader uses Digest authentication following <a href="https://datatracker.ietf.org/doc/html/rfc7616">RFC7616</a>.<br>
 * For API documentation see: <a href="https://shelly-api-docs.shelly.cloud/gen2/General/RPCProtocol">Shelly Gen 2+ Api Docs</a>
 */
public class RpcReader implements IReader {

    private static final Logger LOGGER = Logger.getLogger(RpcReader.class.getName());
    private static final String SHELLY_RPC_PATH = "/rpc";
    private static final String SHELLY_RPC_URI = "http://{}" + SHELLY_RPC_PATH;
    private static final JsonObject RPC_STATUS_BODY = new JsonObject()
            .put("id", 1)
            .put("method", "Shelly.GetStatus");
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // fields to parse Json response
    private static final String JSON_RESULT_NODE = "result";
    private static final String JSON_SWITCH_NODE = "switch:0";
    private static final String JSON_TOTAL_ENERGY_NODE = "aenergy";
    private static final String JSON_POWER_FIELD = "apower";
    private static final String JSON_TOTAL_ENERGY_FIELD = "total";
    private static final String JSON_SYSTEM_NODE = "sys";
    private static final String JSON_UNIXTIME_FIELD = "unixtime";
    // auth fields
    private static final String AUTH_HEADER_REALM_NAME = "realm";
    private static final String AUTH_HEADER_NONCE_NAME = "nonce";
    private static final String AUTH_HEADER_QOP_NAME = "qop";
    private static final String AUTH_NONCE_COUNT = "00000001";
    private static final String AUTH_HEADER_DIGEST_PREFIX = "Digest ";
    private static final String AUTH_HEADER_FORMAT_STRING =
            AUTH_HEADER_DIGEST_PREFIX + "username=\"" + SHELLY_USER + "\", " + AUTH_HEADER_REALM_NAME + "=\"%s\", " +
                    AUTH_HEADER_NONCE_NAME + "=\"%s\", uri=\"" + SHELLY_RPC_PATH + "\", " +
                    "algorithm=SHA-256, response=\"%s\", " + AUTH_HEADER_QOP_NAME + "=\"%s\", nc=%s, cnonce=\"%s\"";
    // headers
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String AUTHORIZATION = "Authorization";
    private static final String SHA_256 = "SHA-256";

    private final String ip;
    private final String password;
    private final HttpRequest requestWithoutAuthorization;

    public RpcReader(@Nonnull String ip, String password) {
        this.ip = ip;
        this.password = password;
        requestWithoutAuthorization = HttpRequest.newBuilder()
                .uri(URI.create(MessageFormatter.format(SHELLY_RPC_URI, ip).getMessage()))
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(RPC_STATUS_BODY.encode()))
                .build();
    }

    @Override
    public Optional<Meter> readPowerConsumption() throws IOException, InterruptedException {
        // Try request without authentication
        HttpResponse<String> response = CLIENT.send(requestWithoutAuthorization, HttpResponse.BodyHandlers.ofString());

        return switch (response.statusCode()) {
            case OK -> Optional.ofNullable(parsePowerConsumption(response.body()));
            case UNAUTHORIZED -> {
                HttpResponse<String> authResponse = CLIENT.send(buildDigestAuthorizedRequest(ip, password, response),
                        HttpResponse.BodyHandlers.ofString());
                if (authResponse.statusCode() != OK) {
                    LOGGER.warning("Authorized Data retrieval error. HTTP-Status: " + response.statusCode());
                    LOGGER.warning("Header:" + authResponse.headers());
                    LOGGER.warning("Response Body: " + response.body());
                    yield Optional.empty();
                }
                yield Optional.ofNullable(parsePowerConsumption(authResponse.body()));
            }
            default -> {
                LOGGER.warning("Data retrieval error. HTTP-Status: " + response.statusCode());
                LOGGER.warning("Header:" + response.headers());
                LOGGER.warning("Response Body: " + response.body());
                yield Optional.empty();
            }
        };
    }

    static Meter parsePowerConsumption(final String jsonResponse) throws JsonProcessingException {
        Meter meter = new Meter();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode switchNode = rootNode.path(JSON_RESULT_NODE).path(JSON_SWITCH_NODE);
        JsonNode sysNode = rootNode.path(JSON_RESULT_NODE).path(JSON_SYSTEM_NODE);
        JsonNode totalEnergyNode = switchNode.path(JSON_TOTAL_ENERGY_NODE);

        if (!sysNode.isMissingNode()) meter.setTimestamp(sysNode.path(JSON_UNIXTIME_FIELD).asLong());
        if (!switchNode.isMissingNode()) {
            meter.setPower(switchNode.path(JSON_POWER_FIELD).asDouble());
            if (!totalEnergyNode.isMissingNode()) {
                meter.setTotal(totalEnergyNode.path(JSON_TOTAL_ENERGY_FIELD).asLong());
            }
            return meter;
        } else {
            LOGGER.warning("No meters found in JSON.");
            return null;
        }
    }

    private HttpRequest buildDigestAuthorizedRequest(String ip, String password, HttpResponse<String> unauthorizedResponse) {
        String header = unauthorizedResponse.headers().firstValue("WWW-Authenticate").orElseThrow();
        if (!header.startsWith(AUTH_HEADER_DIGEST_PREFIX)) {
            throw new IllegalArgumentException("Unknown authentication method: " + header);
        }
        Map<String, String> authHeaderMap = parseDigestAuthHeader(header);
        String realm = authHeaderMap.get(AUTH_HEADER_REALM_NAME);
        String nonce = authHeaderMap.get(AUTH_HEADER_NONCE_NAME);
        String qop = authHeaderMap.get(AUTH_HEADER_QOP_NAME);
        String nonceCount = AUTH_NONCE_COUNT;
        String clientNonce = generateCnonce();
        String ha1 = encodeSha256Hex(String.join(":",
                SHELLY_USER, realm, password));
        String ha2 = encodeSha256Hex(String.join(":",
                unauthorizedResponse.request().method(), SHELLY_RPC_PATH));
        String response = encodeSha256Hex(String.join(":",
                ha1, nonce, nonceCount, clientNonce, "auth", ha2));
        String authHeader = String.format(AUTH_HEADER_FORMAT_STRING, realm, nonce, response, qop, nonceCount, clientNonce);
        LOGGER.log(DEBUG, "Generated auth header: " + authHeader);
        return HttpRequest.newBuilder()
                .uri(URI.create(MessageFormatter.format(SHELLY_RPC_URI, ip).getMessage()))
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(AUTHORIZATION, authHeader)
                .POST(HttpRequest.BodyPublishers.ofString(RPC_STATUS_BODY.encode()))
                .build();
    }

    private String encodeSha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> parseDigestAuthHeader(String header) {
        Map<String, String> result = new HashMap<>();
        String cleanedHeader = header.replaceFirst(AUTH_HEADER_DIGEST_PREFIX, "");

        // Extract all key-value pairs
        Pattern pattern = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]*))");
        Matcher matcher = pattern.matcher(cleanedHeader);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            result.put(key, value);
        }
        LOGGER.log(DEBUG, "Parsed auth header: " + result);
        return result;
    }

    private static String generateCnonce() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes)
                .replaceAll("[^a-zA-Z0-9]", "");
    }

}
