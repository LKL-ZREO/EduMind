package com.firedemo.demo.DTO.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyPasswordResponse {
    private int ret;
    private JData jData;

    // getters and setters
    public int getRet() { return ret; }
    public void setRet(int ret) { this.ret = ret; }
    public JData getjData() { return jData; }
    public void setjData(JData jData) { this.jData = jData; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JData {
        private DataWrapper data;

        public DataWrapper getData() { return data; }
        public void setData(DataWrapper data) { this.data = data; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private DataContent data;

        public DataContent getData() { return data; }
        public void setData(DataContent data) { this.data = data; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataContent {
        private List<PasswordItem> list;

        public List<PasswordItem> getList() { return list; }
        public void setList(List<PasswordItem> list) { this.list = list; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PasswordItem {
        private String mapName;
        private String secret;

        public String getMapName() { return mapName; }
        public void setMapName(String mapName) { this.mapName = mapName; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }
}