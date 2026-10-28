package dev.workflowcrm.integrations.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.integrations.api.GeocodingUnavailableException;
import dev.workflowcrm.integrations.api.ReverseGeocoder;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        name = ExternalIntegrationsMode.PROPERTY,
        havingValue = ExternalIntegrationsMode.LIVE,
        matchIfMissing = true)
public class NominatimReverseGeocoder implements ReverseGeocoder {
    private static final Logger log = LoggerFactory.getLogger(NominatimReverseGeocoder.class);
    private final RestClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final URI endpoint;

    public NominatimReverseGeocoder(
            StringRedisTemplate redis,
            ObjectMapper json,
            @Value("${app.geocoding.url:}") URI endpoint,
            @Value("${app.geocoding.user-agent:WorkflowCRM/0.1 (https://github.com/DrBarga/workflow-crm)}")
                    String agent) {
        this.redis = redis;
        this.json = json;
        this.endpoint = endpoint;
        var http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", agent)
                .build();
    }

    public Location resolve(BigDecimal longitude, BigDecimal latitude) {
        String lon = longitude.stripTrailingZeros().toPlainString(),
                lat = latitude.stripTrailingZeros().toPlainString();
        if (endpoint == null || !endpoint.isAbsolute()) throw new GeocodingUnavailableException();
        String key = "geocoding:v1:" + lat + ":" + lon;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return json.readValue(cached, Location.class);
            if (!Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent("geocoding:rate-limit", "1", Duration.ofSeconds(1))))
                throw new GeocodingUnavailableException();
            var uri = org.springframework.web.util.UriComponentsBuilder.fromUri(endpoint)
                    .queryParam("format", "jsonv2")
                    .queryParam("lat", lat)
                    .queryParam("lon", lon)
                    .queryParam("addressdetails", 1)
                    .queryParam("accept-language", "uk")
                    .build()
                    .toUri();
            var response = client.get().uri(uri).retrieve().body(String.class);
            var node = json.readTree(response);
            var address = node.path("address");
            if (!"ua".equalsIgnoreCase(address.path("country_code").asText()))
                throw new GeocodingUnavailableException();
            String display = node.path("display_name").asText();
            if (display.isBlank()
                    || display.length() > 500
                    || display.contains("<")
                    || display.contains(">")
                    || display.codePoints().anyMatch(Character::isISOControl))
                throw new GeocodingUnavailableException();
            String regionCode = address.path("ISO3166-2-lvl4").asText();
            if (regionCode.isBlank())
                regionCode = address.path("ISO3166-2-lvl6").asText();
            String regionName = address.path("state").asText();
            if (regionName.isBlank()) regionName = address.path("city").asText();
            var location = new Location(display, regionCode, regionName);
            redis.opsForValue().set(key, json.writeValueAsString(location), Duration.ofDays(7));
            return location;
        } catch (GeocodingUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Reverse geocoding unavailable ({})", exception.getClass().getSimpleName());
            throw new GeocodingUnavailableException();
        }
    }
}
