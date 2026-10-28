package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.workflowcrm.integrations.api.GeocodingUnavailableException;
import dev.workflowcrm.integrations.infrastructure.NominatimForwardGeocoder;
import dev.workflowcrm.shared.error.RequestRateLimitException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class ForwardGeocodingIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void providerRequestsAreUkrainianCachedBoundedAndRateLimited() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        var calls = new AtomicInteger();
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("User-Agent")).isEqualTo("WorkflowCRM test");
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            assertThat(query)
                    .contains(
                            "q=київ хрещатик", "countrycodes=ua", "accept-language=uk", "limit=6", "addressdetails=1");
            byte[] body =
                    ("""
                    [{"display_name":"Хрещатик, Київ","lat":"50.450100123","lon":"30.5234","address":{"country_code":"ua"}},
                    {"display_name":"Other country","lat":"50","lon":"30","address":{"country_code":"pl"}},
                    {"display_name":"<script>","lat":"50","lon":"30","address":{"country_code":"ua"}}]
                    """)
                            .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            var adapter = new NominatimForwardGeocoder(
                    redis,
                    new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/search"),
                    "WorkflowCRM test");
            var first = adapter.search("Київ   Хрещатик");
            assertThat(first).hasSize(1);
            assertThat(first.getFirst().latitude()).isEqualByComparingTo("50.4501001");
            assertThat(adapter.search(" київ хрещатик ")).isEqualTo(first);
            assertThat(calls).hasValue(1);
            assertThatThrownBy(() -> adapter.search("Different")).isInstanceOf(RequestRateLimitException.class);
            assertThat(calls).hasValue(1);
            redis.delete("geocoding:rate-limit");
            server.stop(0);
            assertThatThrownBy(() -> adapter.search("Unavailable")).isInstanceOf(GeocodingUnavailableException.class);
        } finally {
            server.stop(0);
            factory.destroy();
        }
    }

    @Test
    void emptyResultsAreCachedButMalformedResponsesAreRetried() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        var calls = new AtomicInteger();
        var body = new java.util.concurrent.atomic.AtomicReference<>("[]");
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            redis.delete("geocoding:rate-limit");
            var adapter = new NominatimForwardGeocoder(
                    redis,
                    new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/search"),
                    "WorkflowCRM test");
            assertThat(adapter.search("Not found")).isEmpty();
            assertThat(adapter.search("Not found")).isEmpty();
            assertThat(calls).hasValue(1);
            body.set("{invalid");
            for (int attempt = 0; attempt < 2; attempt++) {
                redis.delete("geocoding:rate-limit");
                assertThatThrownBy(() -> adapter.search("Invalid")).isInstanceOf(GeocodingUnavailableException.class);
            }
            assertThat(calls).hasValue(3);
        } finally {
            server.stop(0);
            factory.destroy();
        }
    }

    @Test
    void missingConfigurationAndPublicAutocompleteAreRejected() {
        for (URI endpoint : new URI[] {
            URI.create(""), URI.create("https://nominatim.openstreetmap.org/search"), URI.create("file:///tmp/search")
        }) {
            var adapter = new NominatimForwardGeocoder(null, new ObjectMapper(), endpoint, "WorkflowCRM test");
            assertThatThrownBy(() -> adapter.search("Київ")).isInstanceOf(GeocodingUnavailableException.class);
        }
    }
}
