package com.gchen.demo.springaidemo;

import com.gchen.demo.springaidemo.entity.ActorsFilms;
import com.gchen.demo.springaidemo.service.PdfToQdrantService;
import com.google.gson.Gson;
import io.qdrant.client.QdrantClient;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import org.springframework.ai.document.Document;

import java.lang.management.GarbageCollectorMXBean;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class ChatController {

    @Autowired
    public ChatClient deepSeekChatClient;

    @Autowired
    public ChatClient.Builder chatClientBuilder;

    @Autowired
    ChatMemoryRepository chatMemoryRepository;
    @Autowired
    ToolCallbackProvider toolCallbackProvider;

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private QdrantVectorStore qdrantVectorStore;

    @GetMapping("/ai/generate")
    public String generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String actor) {

        String text = String.format("现在几点了，你能设置一个 10 分钟后的闹钟吗？");
        Message userMessage = new UserMessage(text);

        String systemText = "你是一个帮助人们查找信息的智能助手。 你的名字是 {name}。 你应该用你的名字回复用户的请求，并且以 {voice} 的风格进行回答。";

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "小智", "voice", "像郭德纲一样"));

        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

//        String systemInstruction = "请用中文回答用户的问题，并且用幽默的语气回答问题，比如郭德纲的口吻回答！！";
        //用户唯一ID
        String conversationId = "111111111";

        // 1. 更规范的系统指令设置方式
//        SystemMessage systemMessage = new SystemMessage(systemInstruction);

        //初始化基于内存的对话记忆
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
        // 3. 构建Prompt（包含系统消息和用户消息）
//        List<Message> messages = new ArrayList<>();
//        messages.add(systemMessage);
//        Prompt prompt = new Prompt(messages);
//        List<ActorsFilms> entity = deepSeekChatClient.prompt(prompt)
//                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
//                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
//                .call()
//                .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {
//                });
//        return new Gson().toJson(entity);

        String entity = deepSeekChatClient.prompt(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(new DateTimeTools())
                .call()
                .content();
        return entity;
    }

    @GetMapping("/ai/generateTools")
    public String generateTools(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        String conversationId = "111111111";
        ToolCallback[] dateTimeTools = ToolCallbacks.from(new DateTimeTools());
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(dateTimeTools)
                .build();
        Prompt prompt = new Prompt("明天是星期几？", chatOptions);
        //初始化基于内存的对话记忆
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();

        Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClientBuilder.build().mutate())
                        .build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.50)
                        .vectorStore(qdrantVectorStore)
                        .build())
                .build();

        String entity = deepSeekChatClient.prompt(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        return entity;
    }

    @PostMapping(value = "/ai/generateStream1", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateStream(@RequestBody RequestData request) {
        String systemInstruction = "你是一个旅游规划师，你会详细的规划所有旅游地点的线路、酒店，请用中文回答。";
        String message = request.getMessage();
        //用户唯一ID
        String conversationId = request.getConversationId();

        // 1. 更规范的系统指令设置方式
        SystemMessage systemMessage = new SystemMessage(systemInstruction);
        UserMessage userMessage = new UserMessage(message);

        //初始化基于内存的对话记忆
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
        // 3. 构建Prompt（包含系统消息和用户消息）
        List<Message> messages = chatMemory.get(conversationId);
        messages.add(systemMessage);
        Prompt prompt = new Prompt(messages);

        return deepSeekChatClient.prompt(prompt).user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolCallbacks(toolCallbackProvider)
                .stream().content();
//        ChatClient chatClient = ChatClient.builder(deepSeekChatModel)
//                .defaultToolCallbacks(toolCallbackProvider)
//                .build();
//        String content = chatClient.prompt(prompt)
//                .call()
//                .content();
//        System.out.println("------------------------>"+content);
//        assert content != null;
//        UserMessage contentMessage = new UserMessage(content);
//        prompt = new Prompt(List.of(systemMessage, userMessage,contentMessage));
//        ChatClient chatClient1 = ChatClient.builder(deepSeekChatModel)
//                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
//                .build();
//        return chatClient1.prompt(prompt)
//                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
//                .stream().content();
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


//    @PostMapping("/add")
//    public String add(@RequestParam String question) {
//
//        List<Document> documents = List.of(
//                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
//                new Document("The World is Big and Salvation Lurks Around the Corner"),
//                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));
//        Document document = new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1"));
//        // 嵌入文本
//        float[] embedding = embeddingModel.embed(document);
//        // 构造向量对象
//
//        // 存入 Qdrant
//        vectorStore.doAdd(documents);
//        return "ok";
//    }



    @GetMapping("/ai/embedding")
    public Map embed(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        List<Document> results = qdrantVectorStore.similaritySearch(SearchRequest.builder().query(message).topK(8).build());
        return Map.of("embedding", results);

    }

    @Resource
    private PdfToQdrantService pdfToQdrantService;

    @GetMapping("/ai/importFile")
    public String importFile(@RequestParam(value = "message", defaultValue = "/Users/gang/Downloads/昆仑系统-风控相关内容指导手册.pdf") String message) {
        try {
            pdfToQdrantService.importPdfToQdrant(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "ok";
    }

    @GetMapping("/ai/transformer")
    public String transformer(@RequestParam(value = "message", defaultValue = "/Users/gang/Downloads/昆仑系统-风控相关内容指导手册.pdf") String message) {
//        Query query = new Query("Hvad er Danmarks hovedstad?");
//        QueryTransformer queryTransformer = TranslationQueryTransformer.builder()
//                .chatClientBuilder(chatClientBuilder)
//                .targetLanguage("chinese")
//                .build();
//        Query transformedQuery = queryTransformer.transform(query);

//        Query query = new Query("I'm studying machine learning. What is an LLM?");
//
//        QueryTransformer queryTransformer = RewriteQueryTransformer.builder()
//                .chatClientBuilder(chatClientBuilder)
//                .build();
//
//        Query transformedQuery = queryTransformer.transform(query);

//        Query query = Query.builder()
//                .text("And what is its second largest city?")
//                .history(new UserMessage("What is the capital of Denmark?"),
//                        new AssistantMessage("Copenhagen is the capital of Denmark."))
//                .build();
//
//        QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
//                .chatClientBuilder(chatClientBuilder)
//                .build();
//
//        Query transformedQuery = queryTransformer.transform(query);


        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(qdrantVectorStore)
                .similarityThreshold(0.5)
                .topK(5)
//                .filterExpression(new FilterExpressionBuilder()
//                        .eq("genre", "fairytale")
//                        .build())
                .build();
        List<Document> documents = retriever.retrieve(new Query("超额了怎么办？"));
        return new Gson().toJson(documents);
    }

    @PostMapping(value = "/ai/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> answer(@RequestBody RequestData request) {
        Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClientBuilder.build().mutate())
                        .build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.50)
                        .vectorStore(qdrantVectorStore)
                        .build())
                .build();
//        String systemInstruction = "请用中文回答用户的问题，并且用幽默的语气回答问题，比如郭德纲的口吻回答！！";
        //用户唯一ID
        String conversationId = request.getConversationId();

        // 1. 更规范的系统指令设置方式
//        SystemMessage systemMessage = new SystemMessage(systemInstruction);

        //初始化基于内存的对话记忆
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
        Message userMessage = new UserMessage(request.getMessage());

        String systemText = "你是一个帮助人们查找信息的智能助手。 你的名字是 {name}。 " +
                "你应该用你的名字回复用户的请求，并且以 {voice} 的风格进行回答。如果问题中跟时间有关，请调用工具";

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "小智", "voice", "像郭德纲一样"));
        ToolCallback[] dateTimeTools = ToolCallbacks.from(new DateTimeTools());
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(dateTimeTools)
                .build();
        Prompt prompt = new Prompt(List.of(userMessage,systemMessage),chatOptions);
        return deepSeekChatClient.prompt(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
//                .advisors(retrievalAugmentationAdvisor)
                .toolCallbacks(toolCallbackProvider)
                .stream()
                .content();
    }
}
