package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatAppDocumentLoaderTest {

    @Resource
    private ChatAppDocumentLoader chatAppDocumentLoader;

    @Test
    void loadMarkdowns() {
        chatAppDocumentLoader.loadMarkdowns();
    }
}