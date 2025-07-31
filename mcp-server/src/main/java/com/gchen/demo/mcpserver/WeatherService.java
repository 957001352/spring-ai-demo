package com.gchen.demo.mcpserver;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WeatherService {

    @Tool(name="getWeather",description = "根据城市的名称获取天气")
    public Map<String, Object> getWeather(String cityName){
        Map<String, Object> result = new HashMap<>();
        result.put("city", cityName);
        result.put("temperature", "32°C");
        result.put("weather", "龙卷风");
        return result;
    }
}
