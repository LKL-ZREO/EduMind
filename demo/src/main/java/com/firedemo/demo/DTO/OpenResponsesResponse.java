package com.firedemo.demo.DTO;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OpenResponsesResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<OutputItem> output;
    private Usage usage;
    private Error error;

    @Data
    public static class OutputItem {
        private String type;  // "message", "function_call"
        private String id;
        private String status;
        private String role;  // for message
        private Object content;  // List<ContentPart> for message
        private String callId;   // for function_call
        private String name;     // for function_call
        private String arguments; // for function_call
    }

    @Data
    public static class ContentPart {
        private String type;  // "output_text"
        private String text;
        private List<Annotation> annotations;
    }

    @Data
    public static class Annotation {
        private String type;
        private String url;
        private String title;
    }

    @Data
    public static class Usage {
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
    }

    @Data
    public static class Error {
        private String message;
        private String type;
        private String code;
    }
}
