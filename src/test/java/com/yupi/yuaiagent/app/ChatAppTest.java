package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatAppTest {

    @Resource
    private ChatApp chatApp;

    @Test
    void test() {
        String chatId = UUID.randomUUID().toString();
        String message = "很高兴认识你";
        String answer = chatApp.doChat(chatId, message);
        Assertions.assertNotNull(answer);

        message = "我们恋爱吧";
        answer = chatApp.doChat(chatId, message);
        Assertions.assertNotNull(answer);

        message = "结婚吧";
        answer = chatApp.doChat(chatId, message);
        Assertions.assertNotNull(answer);
    }
}