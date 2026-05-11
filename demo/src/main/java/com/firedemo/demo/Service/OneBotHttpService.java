package com.firedemo.demo.Service;

import com.firedemo.demo.config.properties.OneBotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * OneBot HTTP 服务 - 直接调用 Napcat 接口发送消息
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OneBotHttpService {

    private final OneBotProperties properties;
    private final WebClient webClient = WebClient.create();

    /**
     * 发送私聊消息
     */
    public void sendPrivateMessage(String qqNumber, String message) {
        if (qqNumber == null || qqNumber.isEmpty()) {
            log.warn("QQ号为空，无法发送私聊消息");
            return;
        }
        
        try {
            Map<String, Object> body = Map.of(
                    "user_id", qqNumber,
                    "message", message
            );

            sendRequest("/send_private_msg", body);
            log.info("私聊消息已发送: QQ={}, msg={}", qqNumber, message.substring(0, Math.min(30, message.length())));
        } catch (Exception e) {
            log.error("发送私聊消息异常: QQ={}", qqNumber, e);
        }
    }

    /**
     * 发送群消息
     */
    public void sendGroupMessage(String groupId, String message) {
        if (groupId == null || groupId.isEmpty()) {
            log.warn("群号为空，无法发送群消息");
            return;
        }
        
        try {
            Map<String, Object> body = Map.of(
                    "group_id", groupId,
                    "message", message
            );

            sendRequest("/send_group_msg", body);
            log.info("群消息已发送: group={}, msg={}", groupId, message.substring(0, Math.min(30, message.length())));
        } catch (Exception e) {
            log.error("发送群消息异常: group={}", groupId, e);
        }
    }

    /**
     * 发送请求
     */
    private void sendRequest(String endpoint, Map<String, Object> body) {
        String token = properties.getHttp().getToken();
        
        var requestSpec = webClient.post()
                .uri(properties.getHttp().getUrl() + endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
        
        // 如果有token，添加Authorization头
        if (token != null && !token.isEmpty() && !"YOUR_NAPCAT_TOKEN".equals(token)) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + token);
        }
        
        requestSpec
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.error("OneBot请求失败: endpoint={}", endpoint, e))
                .subscribe();
    }
}
