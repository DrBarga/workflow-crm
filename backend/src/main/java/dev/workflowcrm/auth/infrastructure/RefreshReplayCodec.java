package dev.workflowcrm.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.workflowcrm.auth.domain.TokenPair;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class RefreshReplayCodec {
    private static final byte[] CONTEXT = "workflow:refresh-replay:v1".getBytes(StandardCharsets.US_ASCII);
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper json;

    RefreshReplayCodec(ObjectMapper json) {
        this.json = json;
    }

    String encrypt(String presentedToken, TokenPair pair) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            byte[] encrypted =
                    cipher(Cipher.ENCRYPT_MODE, presentedToken, nonce).doFinal(json.writeValueAsBytes(pair));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (java.io.IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect refresh response", exception);
        }
    }

    TokenPair decrypt(String presentedToken, String value) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return json.readValue(
                    cipher(Cipher.DECRYPT_MODE, presentedToken, nonce).doFinal(encrypted), TokenPair.class);
        } catch (java.io.IOException
                | GeneralSecurityException
                | IllegalArgumentException
                | java.nio.BufferUnderflowException exception) {
            throw new IllegalStateException("Unable to read refresh response", exception);
        }
    }

    private Cipher cipher(int mode, String token, byte[] nonce) throws GeneralSecurityException {
        // The random, client-held token is required to recover a grace response; its hash cannot decrypt it.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(CONTEXT);
        digest.update((byte) 0);
        byte[] key = digest.digest(token.getBytes(StandardCharsets.US_ASCII));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(CONTEXT);
        return cipher;
    }
}
