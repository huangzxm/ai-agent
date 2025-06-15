package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.rag.QueryRewriter;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

@TestConfiguration
public class TestConfig {
    
    @Bean(name = "pgVectorVectorStore")
    public VectorStore pgVectorVectorStore() {
        return Mockito.mock(VectorStore.class);
    }
    
    @Bean(name = "loveAppVectorStore")
    public VectorStore loveAppVectorStore() {
        return Mockito.mock(VectorStore.class);
    }
    
    @Bean(name = "loveAppRagCloudAdvisor")
    public Advisor loveAppRagCloudAdvisor() {
        return Mockito.mock(Advisor.class);
    }
    
    @Bean
    public ToolCallback[] allTools() {
        return new ToolCallback[0];
    }
    
    @Bean
    public ToolCallbackProvider toolCallbackProvider() {
        return Mockito.mock(ToolCallbackProvider.class);
    }
    
    @Bean
    public QueryRewriter queryRewriter() {
        QueryRewriter rewriter = Mockito.mock(QueryRewriter.class);
        // 设置当调用doQueryRewrite方法时，直接返回输入参数
        Mockito.when(rewriter.doQueryRewrite(Mockito.anyString())).thenAnswer(i -> i.getArgument(0));
        return rewriter;
    }
    
    @Bean
    public ChatModel dashscopeChatModel() {
        ChatModel mockChatModel = Mockito.mock(ChatModel.class);
        ChatResponse mockResponse = Mockito.mock(ChatResponse.class);
        
        // 配置模拟行为
        Mockito.when(mockChatModel.call(Mockito.any(Prompt.class))).thenReturn(mockResponse);
        Mockito.when(mockChatModel.stream(Mockito.any(Prompt.class))).thenReturn(Flux.empty());
        
        return mockChatModel;
    }
} 