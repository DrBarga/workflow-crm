package dev.workflowcrm.objects.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.objects.domain.*;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class EncryptedObjectListCursors implements ObjectListCursors {
    private final ObjectMapper json;
    private final Clock clock;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    EncryptedObjectListCursors(
            ObjectMapper json,
            Clock clock,
            @Value("${app.objects.list.cursor-secret:${app.security.jwt.secret}}") String secret)
            throws GeneralSecurityException {
        if (secret.length() < 32)
            throw new IllegalArgumentException("Object list cursor secret must contain at least 32 characters");
        this.json = json;
        this.clock = clock;
        key = new SecretKeySpec(
                MessageDigest.getInstance("SHA-256")
                        .digest(("workflow-object-list-v1:" + secret).getBytes(StandardCharsets.UTF_8)),
                "AES");
    }

    public ObjectListPosition read(String token, ObjectListFilter filter, ObjectListScope scope) {
        try {
            if (token.length() > MAX_LENGTH || !token.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length < 29) throw new IllegalArgumentException();
            byte[] iv = java.util.Arrays.copyOf(bytes, 12);
            var cipher = cipher(Cipher.DECRYPT_MODE, iv, filter, scope);
            var result = json.readValue(cipher.doFinal(bytes, 12, bytes.length - 12), ObjectListPosition.class);
            if (result.id() == null
                    || result.snapshot() == null
                    || result.expiresAt() == null
                    || !result.expiresAt().isAfter(clock.instant())
                    || result.snapshot().isAfter(clock.instant())) throw new IllegalArgumentException();
            return result;
        } catch (Exception e) {
            throw new UnprocessableInputException("cursor", "Cursor is invalid, expired or belongs to another list");
        }
    }

    public String write(ObjectListPosition position, ObjectListFilter filter, ObjectListScope scope) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, iv, filter, scope).doFinal(json.writeValueAsBytes(position));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create object list cursor", e);
        }
    }

    private Cipher cipher(int mode, byte[] iv, ObjectListFilter filter, ObjectListScope scope) throws Exception {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(json.writeValueAsBytes(new Context(2, filter, scope)));
        return cipher;
    }

    private record Context(int version, ObjectListFilter filter, ObjectListScope scope) {}
}
