package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.workflowcrm.integrations.api.GeocodingUnavailableException;
import dev.workflowcrm.integrations.infrastructure.NominatimReverseGeocoder;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class NominatimGeocodingIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void callsHttpAdapterWithAttributionCachesResultsAndRejectsUnavailableProvider() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        var calls = new AtomicInteger();
        server.createContext("/reverse", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("User-Agent")).isEqualTo("WorkflowCRM test");
            assertThat(exchange.getRequestURI().getQuery())
                    .contains("format=jsonv2", "lat=50.45", "lon=30.52", "accept-language=uk");
            byte[] body =
                    "{\"display_name\":\"Київ\",\"address\":{\"country_code\":\"ua\",\"state\":\"Київ\",\"ISO3166-2-lvl4\":\"UA-30\"}}"
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            var redis = new StringRedisTemplate(factory);
            var adapter = new NominatimReverseGeocoder(
                    redis,
                    new ObjectMapper(),
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/reverse"),
                    "WorkflowCRM test");
            var first = adapter.resolve(new BigDecimal("30.52"), new BigDecimal("50.45"));
            var second = adapter.resolve(new BigDecimal("30.5200"), new BigDecimal("50.4500"));
            assertThat(first).isEqualTo(second);
            assertThat(first.regionCode()).isEqualTo("UA-30");
            assertThat(calls).hasValue(1);
            redis.delete("geocoding:rate-limit");
            server.stop(0);
            assertThatThrownBy(() -> adapter.resolve(new BigDecimal("30.53"), new BigDecimal("50.45")))
                    .isInstanceOf(GeocodingUnavailableException.class);
            assertThat(redis.hasKey("geocoding:v1:50.45:30.53")).isFalse();
        } finally {
            server.stop(0);
            factory.destroy();
        }
    }
}
