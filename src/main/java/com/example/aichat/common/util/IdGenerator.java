package com.example.aichat.common.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private IdGenerator() {}

    public static String generate(String prefix) {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return prefix + "_" + ENCODER.encodeToString(bytes);
    }

    public static String generateConvId() {
        return generate("conv");
    }

    public static String generateMessageId() {
        return generate("msg");
    }

    public static String generateUserId() {
        return generate("user");
    }

    public static String generateRequestId() {
        return generate("req");
    }
}
