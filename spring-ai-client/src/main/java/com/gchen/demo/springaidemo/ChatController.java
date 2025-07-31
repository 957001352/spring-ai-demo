package com.gchen.demo.springaidemo;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import org.springframework.ai.document.Document;

import java.util.*;

@RestController
public class ChatController {

    private final DeepSeekChatModel deepSeekChatModel;

    @Autowired
    public ChatController(DeepSeekChatModel ollamaChatModel) {
        this.deepSeekChatModel = ollamaChatModel;
    }

    @Autowired
    ChatMemoryRepository chatMemoryRepository;
    @Autowired
    ToolCallbackProvider toolCallbackProvider;

    @GetMapping("/ai/generate")
    public String generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        Prompt prompt = new Prompt(message);
        AssistantMessage output = deepSeekChatModel.call(prompt).getResult().getOutput();
        return output.getText();
    }

//    @PostMapping("/ai/generateTools")
//    public Flux<String> generateTools(@RequestBody RequestData request) {
//        String systemInstruction = "你是个乐于助人的助手，请用中文回答。";
//        String message = request.getMessage();
//        //用户唯一ID
//        String conversationId = request.getConversationId();
//        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
//        ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
//
//        ChatOptions chatOptions = ToolCallingChatOptions.builder()
//                .toolCallbacks(ToolCallbacks.from(new DateTimeTools()))
//                .internalToolExecutionEnabled(false)
//                .build();
//        Prompt prompt = new Prompt(
//                List.of(new SystemMessage(systemInstruction), new UserMessage(message)),
//                chatOptions);
//        chatMemory.add(conversationId, prompt.getInstructions());
//
//        Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
//        ChatResponse chatResponse = ollamaChatModel.call(promptWithMemory);
//        chatMemory.add(conversationId, chatResponse.getResult().getOutput());
//
//        while (chatResponse.hasToolCalls()) {
//            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory,
//                    chatResponse);
//            chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
//                    .get(toolExecutionResult.conversationHistory().size() - 1));
//            promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
//            chatResponse = ollamaChatModel.call(promptWithMemory);
//            chatMemory.add(conversationId, chatResponse.getResult().getOutput());
//        }
//
//        UserMessage newUserMessage = new UserMessage(message);
//        chatMemory.add(conversationId, newUserMessage);
//
//        Flux<String> flux = ollamaChatModel.stream(new Prompt(chatMemory.get(conversationId)))
//                .map(ChatResponse::getResults)
//                .flatMapIterable(list -> list == null ? Collections.emptyList() : list)
//                .concatMap(result -> {
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
//        return flux;
//    }

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

        ChatClient chatClient = ChatClient.builder(deepSeekChatModel)
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
        String content = chatClient.prompt(prompt)
                .call()
                .content();
        System.out.println("------------------------>"+content);
        assert content != null;
        UserMessage contentMessage = new UserMessage(content);
        prompt = new Prompt(List.of(systemMessage, userMessage,contentMessage));
        ChatClient chatClient1 = ChatClient.builder(deepSeekChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        return chatClient1.prompt(prompt)
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

//    @Resource
//    private EmbeddingModel embeddingModel;

//    @PostMapping("/ask")
//    public String ask(@RequestParam String question) {
//
//        List<Document> theWorld = vectorStore.similaritySearch(
//                        SearchRequest.builder()
//                        .query("The World")
//                        .topK(5)
//                        .similarityThreshold(20)
//                        .filterExpression("author in ['john', 'jill'] && article_type == 'blog'").build());
//        System.out.println(new Gson().toJson(theWorld));
//        return "ok";
//    }
//
    @Autowired
    EmbeddingModel embeddingModel;
    @Autowired
    QdrantVectorStore vectorStore;
    @Autowired
    QdrantClient qdrantClient;

    @PostMapping("/add")
    public String add(@RequestParam String question) {

        List<Document> documents = List.of(
                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
                new Document("The World is Big and Salvation Lurks Around the Corner"),
                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));
        Document document = new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1"));
        // 嵌入文本
        float[] embedding = embeddingModel.embed(document);
        // 构造向量对象

        // 存入 Qdrant
        vectorStore.doAdd(documents);
        return "ok";
    }
}
