package com.romanysrael.battleofbluffs.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public final class SecureAccountTokenGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] entropy = new byte[32];
        secureRandom.nextBytes(entropy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormatSupport.lowercase(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class HexFormatSupport {
        private static final char[] HEX = "0123456789abcdef".toCharArray();

        private HexFormatSupport() {
        }

        static String lowercase(byte[] bytes) {
            char[] result = new char[bytes.length * 2];
            for (int index = 0; index < bytes.length; index++) {
                int value = bytes[index] & 0xff;
                result[index * 2] = HEX[value >>> 4];
                result[index * 2 + 1] = HEX[value & 0x0f];
            }
            return new String(result);
        }
    }
}
