package com.prootshell.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ChatModels {
    public static class ChatRequest {
        public String model = "gpt-3.5-turbo";
        public List<Message> messages;
        public double temperature = 0.2;
        public boolean stream = false;
    }

    public static class Message {
        public String role;
        public String content;
        public Message(String role, String content) { this.role = role; this.content = content; }
    }

    public static class ChatResponse {
        public List<Choice> choices;
        public Usage usage;
    }

    public static class Choice {
        public Message message;
        @SerializedName("finish_reason") public String finishReason;
    }

    public static class Usage {
        public int prompt_tokens;
        public int completion_tokens;
        public int total_tokens;
    }
}