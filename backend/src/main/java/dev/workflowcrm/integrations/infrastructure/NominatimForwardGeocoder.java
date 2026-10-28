package dev.workflowcrm.integrations.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.integrations.api.*;
import dev.workflowcrm.shared.error.RequestRateLimitException;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(
        name = ExternalIntegrationsMode.PROPERTY,
        havingValue = ExternalIntegrationsMode.LIVE,
        matchIfMissing = true)
public class NominatimForwardGeocoder implements ForwardGeocoder {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final URI endpoint;
    private final RestClient client;

    public NominatimForwardGeocoder(
            StringRedisTemplate redis,
            ObjectMapper json,
            @Value("${app.geocoding.search-url:}") URI endpoint,
            @Value("${app.geocoding.user-agent:WorkflowCRM/0.1 (https://github.com/DrBarga/workflow-crm)}")
                    String agent) {
        this.redis = redis;
        this.json = json;
        this.endpoint = endpoint;
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", agent)
                .build();
    }

    public List<Location> search(String query) {
        requireEndpoint();
        String normalized = query.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        try {
            String key = "geocoding:search:v1:"
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                    .digest((endpoint + "\n" + normalized).getBytes(StandardCharsets.UTF_8)));
            String cached = redis.opsForValue().get(key);
            if (cached != null) return List.of(json.readValue(cached, Location[].class));
            if (!Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent("geocoding:rate-limit", "1", Duration.ofSeconds(1))))
                throw new RequestRateLimitException("Geocoding rate limit reached; retry after one second");
            URI uri = UriComponentsBuilder.fromUri(endpoint)
                    .queryParam("format", "jsonv2")
                    .queryParam("q", normalized)
                    .queryParam("countrycodes", "ua")
                    .queryParam("accept-language", "uk")
                    .queryParam("limit", 6)
                    .queryParam("addressdetails", 1)
                    .build()
                    .encode()
                    .toUri();
            var body = client.get().uri(uri).retrieve().body(byte[].class);
            if (body == null || body.length > 262144) throw new GeocodingUnavailableException();
            var root = json.readTree(body);
            if (!root.isArray()) throw new GeocodingUnavailableException();
            var result = new LinkedHashSet<Location>();
            for (var node : root) {
                if (result.size() == 6) break;
                if (!"ua"
                        .equalsIgnoreCase(
                                node.path("address").path("country_code").asText())) continue;
                String label = node.path("display_name").asText();
                if (label.isBlank()
                        || label.length() > 500
                        || label.contains("<")
                        || label.contains(">")
                        || label.codePoints().anyMatch(Character::isISOControl)) continue;
                BigDecimal lat = new BigDecimal(node.path("lat").asText()),
                        lon = new BigDecimal(node.path("lon").asText());
                if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lon.abs().compareTo(BigDecimal.valueOf(180)) > 0)
                    continue;
                result.add(new Location(label, coordinate(lat), coordinate(lon)));
            }
            var locations = List.copyOf(result);
            redis.opsForValue().set(key, json.writeValueAsString(locations), Duration.ofDays(7));
            return locations;
        } catch (RequestRateLimitException | GeocodingUnavailableException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new GeocodingUnavailableException();
        }
    }

    private static BigDecimal coordinate(BigDecimal value) {
        return value.setScale(7, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private void requireEndpoint() {
        String host = endpoint == null ? null : endpoint.getHost();
        if (host == null
                || !Set.of("http", "https").contains(endpoint.getScheme())
                || host.equalsIgnoreCase("nominatim.openstreetmap.org")
                || host.toLowerCase(Locale.ROOT).endsWith(".openstreetmap.org"))
            throw new GeocodingUnavailableException();
    }
}
