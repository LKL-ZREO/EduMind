package com.firedemo.edumind.teaching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 备课学情仪表盘 — 聚合作业错题 + 课堂互动 + 知识图谱，AI 生成备课建议。
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PreLessonDTO {

    private Long classId;
    private String className;

    // ── 作业维度 ──
    private double avgScore;
    private int totalStudents;
    private int warningCount;          // 需关注学生数（均分 < 60）

    // ── 薄弱知识点 TOP5 ──
    private List<WeakPoint> weakPoints;

    // ── 课堂互动维度 ──
    private int liveSessionCount;      // 历史课堂次数
    private double liveAvgCorrectRate; // 互动平均正确率
    private double participationRate;  // 平均参与率

    // ── AI 备课建议 ──
    private String aiSuggestion;       // AI 综合分析文本

    // ── 分层建议 ──
    private List<TierGroup> tieredGroups;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WeakPoint {
        private String name;
        private int errorCount;
        private int mastery;           // 0-100
        private String severity;       // critical / high / medium
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TierGroup {
        private String label;          // "A层（优秀）" / "B层（中等）" / "C层（需关注）"
        private String range;          // "90-100分"
        private int count;
        private String suggestion;     // AI 建议该层学生的教学策略
    }
}
