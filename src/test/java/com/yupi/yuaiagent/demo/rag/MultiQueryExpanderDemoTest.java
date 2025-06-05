package com.yupi.yuaiagent.demo.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultiQueryExpanderDemoTest {

    @Resource
    private MultiQueryExpanderDemo multiQueryExpanderDemo;

    @Test
    void expand() {
        List<Query> expand = multiQueryExpanderDemo.expand(
                "我想了解一下关于我和林晚棠的关系，以及我和林晚棠的故事。"
        );
        Assertions.assertNotNull(expand);
    }
}