package com.gchen.demo.springaidemo;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
//import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
//import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;

@RestController
public class ChatController {

    private final OllamaChatModel ollamaChatModel;

    @Autowired
    public ChatController(OllamaChatModel ollamaChatModel) {
        this.ollamaChatModel = ollamaChatModel;
    }

    @Autowired
    ChatMemoryRepository chatMemoryRepository;

    @GetMapping("/ai/generate")
    public String generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        String response = ChatClient.create(ollamaChatModel)
                .prompt(message)
                .tools(new DateTimeTools())
                .call()
                .content();
        return response;
    }

    @PostMapping(value = "/ai/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateStream(@RequestBody RequestData request) {
        String systemInstruction = "你是一个旅游规划师，你会详细的规划所有旅游地点的线路、酒店，请用中文回答。";
        String message = request.getMessage();
        //用户唯一ID
        String conversationId = request.getConversationId();

        // 1. 更规范的系统指令设置方式
        SystemMessage systemMessage = new SystemMessage(systemInstruction);
        UserMessage userMessage = new UserMessage(message);

        // 2. 工具设置
//        ToolCallback[] dateTimeTools = ToolCallbacks.from(new DateTimeTools());
//        ChatOptions chatOptions = ToolCallingChatOptions.builder()
//                .toolCallbacks(dateTimeTools)
//                .build();

        //初始化基于内存的对话记忆
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();

        // 3. 构建Prompt（包含系统消息和用户消息）
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        ChatClient chatClient = ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        return chatClient.prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream().content();
        // 4. 处理流式响应（包含工具调用结果）
//        return ollamaChatModel.stream(prompt)
//                .map(ChatResponse::getResults)
//                .flatMapIterable(list -> list == null ? Collections.emptyList() : list)
//                .concatMap(result -> {
//                    // 处理工具调用情况
////                    if (result.getOutput() != null) {
////                        if (!result.getOutput().getToolCalls().isEmpty()) {
////                            return Flux.fromIterable(result.getOutput().getToolCalls())
////                                    .map(toolCall -> "工具调用: " + toolCall.name());
////                        }
////                    }
//                    // 处理普通文本响应
//                    if (result.getOutput() != null && result.getOutput().getText() != null) {
//                        return Flux.just(result.getOutput().getText());
//                    }
//                    return Flux.empty();
//                })
//                .onErrorResume(e -> {
//                    System.out.println("处理请求失败");
//                    return Flux.just("错误: " + e.getMessage());
//                });
    }

//    private final DeepSeekChatModel chatModel;
//
//    @Autowired
//    public ChatController(DeepSeekChatModel chatModel) {
//        this.chatModel = chatModel;
//    }
//
//    @GetMapping("/ai/generate")
//    public Map generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
//        return Map.of("generation", chatModel.call(message));
//    }
//
//    @GetMapping("/ai/generateStream")
//    public Flux<ChatResponse> generateStream(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
//        var prompt = new Prompt(new UserMessage(message));
//        return chatModel.stream(prompt);
//    }
//
//    @GetMapping("/ai/generatePythonCode")
//    public String generatePythonCode(@RequestParam(value = "message", defaultValue = "Please write quick sort code") String message) {
//        UserMessage userMessage = new UserMessage(message);
//        Message assistantMessage = DeepSeekAssistantMessage.prefixAssistantMessage("```python\\n");
//        Prompt prompt = new Prompt(List.of(userMessage, assistantMessage), ChatOptions.builder().stopSequences(List.of("```")).build());
//        ChatResponse response = chatModel.call(prompt);
//        return response.getResult().getOutput().getText();
//    }
}
