package com.gchen.demo.springaidemo;


// 定义请求体DTO
public class RequestData {
    private String message;
    private String conversationId;
    public String getConversationId() {
        return conversationId;
    }
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    // getter/setter
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
