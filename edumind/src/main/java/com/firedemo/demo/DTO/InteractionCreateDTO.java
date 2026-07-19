package com.firedemo.demo.DTO;
import lombok.Data;
import java.util.List;
@Data
public class InteractionCreateDTO {
    private String type;
    private String title;
    private String description;
    private List<OptionDTO> options;
    private String correctKey;
    private Integer timeLimit;
    private String knowledgePoint;
    @Data
    public static class OptionDTO { private String key; private String text; }
}
