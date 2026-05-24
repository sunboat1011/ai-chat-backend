package com.example.aichat.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IdGeneratorTest {

    @Test
    void testGenerateConvIdPrefix() {
        String id = IdGenerator.generateConvId();
        assertTrue(id.startsWith("conv_"), "会话ID应以 conv_ 开头");
    }

    @Test
    void testGenerateMessageIdPrefix() {
        String id = IdGenerator.generateMessageId();
        assertTrue(id.startsWith("msg_"), "消息ID应以 msg_ 开头");
    }

    @Test
    void testGenerateUserIdPrefix() {
        String id = IdGenerator.generateUserId();
        assertTrue(id.startsWith("user_"), "用户ID应以 user_ 开头");
    }

    @Test
    void testGenerateRequestIdPrefix() {
        String id = IdGenerator.generateRequestId();
        assertTrue(id.startsWith("req_"), "请求ID应以 req_ 开头");
    }

    @Test
    void testUniqueness() {
        int count = 1000;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(IdGenerator.generateConvId());
        }
        assertEquals(count, ids.size(), "生成 " + count + " 个ID应全部唯一");
    }

    @Test
    void testCustomPrefix() {
        String id = IdGenerator.generate("custom");
        assertTrue(id.startsWith("custom_"));
        assertTrue(id.length() > "custom_".length());
    }
}
