package com.firedemo.edumind.live;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
@Data
public class InteractionCreateDTO {
    @NotBlank @Pattern(regexp = "CHOICE|OPEN|EXERCISE")
    private String type;
    @NotBlank @Size(max = 2000)
    private String title;
    private String description;
    @Valid @Size(max = 10)
    private List<OptionDTO> options;
    @NotBlank @Size(max = 2000)
    private String correctKey;
    private Integer timeLimit;
    private String knowledgePoint;
    @Data
    public static class OptionDTO {
        @NotBlank @Size(max = 10) private String key;
        @NotBlank @Size(max = 1000) private String text;
    }
}
