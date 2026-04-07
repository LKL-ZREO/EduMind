package com.firedemo.demo.DTO;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class OpenResponsesRequest {
    private String model;
    private Object input;  // String 或 List<InputItem>
    private String instructions;
    private List<Tool> tools;
    private Object toolChoice;
    private Boolean stream;
    private String user;
    private Integer maxOutputTokens;

    @Data
    @Builder
    public static class InputItem {
        private String type;  // "message", "function_call_output"
        private String role;  // "user", "assistant", "system"
        private Object content;  // String 或 List<ContentPart>
        private String callId;   // for function_call_output
        private String output;   // for function_call_output
    }

    @Data
    @Builder
    public static class ContentPart {
        private String type;  // "input_text", "input_image", "input_file", "output_text"
        private String text;
        private ImageSource imageUrl;
        private FileSource file;
    }

    @Data
    @Builder
    public static class ImageSource {
        private String type;  // "url" or "base64"
        private String url;
        private String data;
    }

    @Data
    @Builder
    public static class FileSource {
        private String type;  // "url" or "base64"
        private String mediaType;
        private String data;
        private String filename;
    }

    @Data
    @Builder
    public static class Tool {
        private String type;  // "function"
        private FunctionDefinition function;
    }

    @Data
    @Builder
    public static class FunctionDefinition {
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }
}
