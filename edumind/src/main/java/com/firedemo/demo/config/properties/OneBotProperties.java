package com.firedemo.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OneBot Napcat 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "onebot")
public class OneBotProperties {

    private Http http = new Http();
    private Ws ws = new Ws();

    @Data
    public static class Http {
        private String url = "http://127.0.0.1:3000";
        private String token = "";
    }

    /**
     * WebSocket 正向连接配置 — Java 作为客户端连接 NapCat 接收 QQ 消息事件
     */
    @Data
    public static class Ws {
        /** 是否启用 WebSocket 接收（默认启用） */
        private boolean enabled = true;
        /** NapCat WebSocket 地址 */
        private String url = "ws://127.0.0.1:3001";
        /** OneBot v11 协议路径 */
        private String path = "/onebot/v11/ws";
        /** WebSocket 握手时的 Access Token */
        private String accessToken = "";
        /** 是否仅响应 @机器人的消息 */
        private boolean requireMention = true;
    }
}
