package com.firedemo.demo.DTO;

public class DailyPasswordDTO {
    private String name;
    private String code;

    public DailyPasswordDTO() {}

    public DailyPasswordDTO(String name, String code) {
        this.name = name;
        this.code = code;

    }
    // getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }


}
