package dev.workflowcrm.auth.infrastructure;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.workflowcrm.auth.domain.PasswordRecoveryProperties;
import dev.workflowcrm.auth.domain.RefreshSessionStore;
import dev.workflowcrm.auth.domain.TokenRevocationStore;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.WebUtils;

@EnableMethodSecurity
@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    LoginProtectionProperties.class,
    RefreshSessionProperties.class,
    DeviceSecurityProperties.class,
    ChallengeProperties.class,
    TelegramProperties.class,
    PasswordRecoveryProperties.class,
    CorsProperties.class
})
class SecurityConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JwtProperties properties)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = new CookieCsrfTokenRepository();
        csrfRepository.setCookieCustomizer(cookie -> cookie.httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path("/"));
        http.cors(Customizer.withDefaults())
                .csrf(csrf ->
                        csrf.csrfTokenRepository(csrfRepository).withObjectPostProcessor(new CookieCsrfProtection()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/set-password",
                                "/api/auth/telegram/webhook",
                                "/api/auth/challenges/**",
                                "/api/user-invitations/open",
                                "/api/user-invitations/accept")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/csrf",
                                "/api/auth/invite/**",
                                "/api/auth/challenges/**",
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(
                                (request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!properties.isEnabled()) {
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(properties.allowedHeaders());
        // Authentication and CSRF live in cookies, the browser sends them cross-origin only with this flag
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(properties.preflightCacheDuration());
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    BearerTokenResolver bearerTokenResolver(JwtProperties properties) {
        return request -> {
            String path =
                    request.getRequestURI().substring(request.getContextPath().length());
            if ("/api/auth/login".equals(path)
                    || "/api/auth/csrf".equals(path)
                    || "/api/auth/refresh".equals(path)
                    || "/api/auth/forgot-password".equals(path)
                    || "/api/auth/reset-password".equals(path)
                    || "/api/auth/set-password".equals(path)
                    || path.startsWith("/api/auth/invite/")
                    || "/api/user-invitations/open".equals(path)
                    || "/api/user-invitations/accept".equals(path)
                    || "/api/auth/telegram/webhook".equals(path)
                    || path.startsWith("/api/auth/challenges/")
                    || "/actuator/health".equals(path)
                    || path.startsWith("/actuator/health/")) {
                return null;
            }
            var cookie = WebUtils.getCookie(request, properties.cookieName());
            return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
        };
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties,
            TokenRevocationStore revocationStore,
            RefreshSessionStore sessionStore) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(timestampValidator, new JwtIssuerValidator(properties.issuer())));
        return new RevocationCheckingJwtDecoder(decoder, revocationStore, sessionStore);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
