package com.firedemo.edumind.live.service;

import com.firedemo.edumind.live.InteractionHistoryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final InteractionService interactionService;
    private final StudentPresenceService presenceService;

    /** 生成 HTML 课程报告 */
    public String generateHtml(Long sessionId, String sessionTitle, String duration,
                               int onlineCount, int absentCount, int qaCount) {
        List<InteractionHistoryDTO> interactions = interactionService.getInteractionHistory(sessionId, null);

        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < interactions.size(); i++) {
            InteractionHistoryDTO h = interactions.get(i);
            String typeName = switch (h.getType()) {
                case "CHOICE" -> "选择题";
                case "OPEN" -> "简答题";
                case "EXERCISE" -> "随堂练习";
                default -> h.getType();
            };
            rows.append(String.format("""
                <tr>
                  <td>%d</td><td>%s</td><td>%s</td>
                  <td>%d/%d</td><td>%s</td>
                </tr>""",
                i + 1, typeName, escape(h.getTitle()),
                h.getRespondedCount(), h.getTotalStudents(),
                h.getCorrectRate() != null ? h.getCorrectRate() + "%" : "-"));
        }

        return String.format("""
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>课堂报告 - %s</title>
            <style>body{font-family:sans-serif;max-width:700px;margin:40px auto;padding:20px}
            h1{color:#333}h2{color:#666;margin-top:24px}table{width:100%%;border-collapse:collapse;margin:12px 0}
            th,td{border:1px solid #ddd;padding:8px 12px;text-align:left}th{background:#f5f5f5}
            .stat{display:inline-block;margin:8px 16px 8px 0;font-size:15px}</style></head>
            <body>
            <h1>📊 %s</h1>
            <p>⏱ 时长: %s | 📋 互动: %d 次 | 👥 在线: %d | ❌ 缺席: %d | 🙋 提问: %d</p>
            <h2>互动详情</h2>
            <table><tr><th>#</th><th>类型</th><th>题目</th><th>已答</th><th>正确率</th></tr>
            %s</table>
            <p style="color:#999;margin-top:30px">EduMind 课堂报告 · 生成时间: %s</p>
            </body></html>""",
                escape(sessionTitle),
                escape(sessionTitle), duration, interactions.size(), onlineCount, absentCount, qaCount,
                rows.toString(),
                java.time.LocalDateTime.now().toString().replace("T", " "));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
