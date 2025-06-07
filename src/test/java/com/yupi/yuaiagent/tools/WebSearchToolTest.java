package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.AiAgentApplication;
import com.yupi.yuaiagent.rag.PgVectorVectorStoreConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class WebSearchToolTest {

    @Value("${search-api.api-key}")
    private String searchApiKey;


    @Test
    public void testSearchWeb() {
        WebSearchTool tool = new WebSearchTool(searchApiKey);
        String query = "怎么学习AI";
        String result = tool.searchWeb(query);
        assertNotNull(result);
    }
}
