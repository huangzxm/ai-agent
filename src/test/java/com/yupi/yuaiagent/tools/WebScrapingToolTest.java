package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebScrapingToolTest {

    @Test
    void scrapeWebPage() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String url = "https://lol.qq.com/news/go.html";
        String result = webScrapingTool.scrapeWebPage(url);
        Assertions.assertNotNull(result);
    }
}
