package dev.workflowcrm.auth.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class DeviceCookie {
    private final DeviceSecurityProperties properties;
    private final JwtProperties jwtProperties;
    private final SecureRandom random = new SecureRandom();

    DeviceCookie(DeviceSecurityProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
    }

    public String name() {
        return properties.cookieName();
    }

    public String newValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String deviceId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            return "";
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String create(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(jwtProperties.secure())
                .sameSite("Strict")
                .path("/")
                .maxAge(properties.trustDuration())
                .build()
                .toString();
    }
}
