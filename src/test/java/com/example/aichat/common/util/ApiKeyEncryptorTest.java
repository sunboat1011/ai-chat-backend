package com.example.aichat.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyEncryptorTest {

    @Test
    void testEncryptDecryptRoundtrip() {
        ApiKeyEncryptor encryptor = new ApiKeyEncryptor();
        ReflectionTestUtils.setField(encryptor, "encryptionKeyBase64",
            "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        encryptor.init();

        String original = "sk-test-api-key-12345";
        String encrypted = encryptor.encrypt(original);
        String decrypted = encryptor.decrypt(encrypted);

        assertNotEquals(original, encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void testEncryptProducesDifferentCiphertexts() {
        ApiKeyEncryptor encryptor = new ApiKeyEncryptor();
        ReflectionTestUtils.setField(encryptor, "encryptionKeyBase64",
            "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        encryptor.init();

        String original = "same-plaintext";
        String encrypted1 = encryptor.encrypt(original);
        String encrypted2 = encryptor.encrypt(original);

        assertNotEquals(encrypted1, encrypted2, "相同明文每次加密应产生不同密文（GCM随机IV）");
        assertEquals(original, encryptor.decrypt(encrypted1));
        assertEquals(original, encryptor.decrypt(encrypted2));
    }

    @Test
    void testDecryptInvalidCiphertextThrowsException() {
        ApiKeyEncryptor encryptor = new ApiKeyEncryptor();
        ReflectionTestUtils.setField(encryptor, "encryptionKeyBase64",
            "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        encryptor.init();

        assertThrows(RuntimeException.class, () -> encryptor.decrypt("invalid-ciphertext"));
    }

    @Test
    void testInitWithInvalidKeyLengthThrowsException() {
        ApiKeyEncryptor encryptor = new ApiKeyEncryptor();
        ReflectionTestUtils.setField(encryptor, "encryptionKeyBase64",
            "c2hvcnRrZXk="); // "shortkey" = 8 bytes

        assertThrows(IllegalStateException.class, encryptor::init);
    }
}
