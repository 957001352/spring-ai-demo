package com.gchen.demo.mcpserver;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@SpringBootApplication
@RestController
@RequestMapping("/weather")
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService){
        return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }

    @PostMapping("/get")
    public Mono<Map<String, Object>> getWeather(@RequestBody Map<String, Object> requestBody) {
        // 参数在 parameters 这个字段中
        Map<String, Object> parameters = (Map<String, Object>) requestBody.get("parameters");
        String city = (String) parameters.get("location");

        String reply = "The weather in " + city + " is sunny and 26°C.";
        return Mono.just(Map.of("output", reply));
    }
}
