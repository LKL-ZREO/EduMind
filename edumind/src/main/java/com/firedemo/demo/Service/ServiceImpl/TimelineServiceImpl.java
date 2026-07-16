package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.DTO.TimelineDTO;
import com.firedemo.demo.DTO.TimelineDTO.WeekGroup;
import com.firedemo.demo.DTO.TimelineDTO.WeekItem;
import com.firedemo.demo.Entity.*;
import com.firedemo.demo.Service.TimelineService;
import com.firedemo.demo.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    private final ClassroomSessionMapper sessionMapper;
    private final HomeworkTaskMapper homeworkTaskMapper;
    private final PreviewTaskMapper previewTaskMapper;
    private final TeachingCalendarMapper calendarMapper;
    private final InteractionMapper interactionMapper;
    private final InteractionResponseMapper responseMapper;
    private final LiveConfusionEventMapper confusionEventMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final WeekFields WF = WeekFields.of(java.util.Locale.CHINA);

    @Override
    public TimelineDTO getTimeline(Long classId, int limit) {
        Map<Integer, List<WeekItem>> weekMap = new LinkedHashMap<>();

        // 1. 教学日历计划
        for (TeachingCalendar p : calendarMapper.findByClassId(classId)) {
            WeekItem item = WeekItem.builder()
                    .type("plan").icon("📅").typeLabel("计划")
                    .id(p.getId()).title(p.getTopic())
                    .date(p.getPlannedDate() != null ? p.getPlannedDate().format(DATE_FMT) : "")
                    .time("").status(p.getStatus()).detail(p.getKnowledgePoints())
                    .build();
            weekMap.computeIfAbsent(weekOf(p.getPlannedDate()), k -> new ArrayList<>()).add(item);
        }

        // 2. 课堂（含互动统计 + 薄弱点）
        for (ClassroomSession s : sessionMapper.findByClassId(classId)) {
            List<Interaction> interactions = interactionMapper.findBySessionId(s.getId());
            int ic = interactions.size();
            double cr = avgCorrectRate(interactions);
            String tc = topConfusion(s.getId());

            StringBuilder detail = new StringBuilder();
            if (ic > 0) detail.append(String.format("互动%d次 正确率%.0f%%", ic, cr));
            if (tc != null) detail.append(" | ⚠️").append(tc);

            weekMap.computeIfAbsent(weekOf(s.getStartedAt()), k -> new ArrayList<>()).add(
                    WeekItem.builder()
                            .type("session").icon("🎓").typeLabel("课堂")
                            .id(s.getId()).title(s.getTitle())
                            .date(s.getStartedAt() != null ? s.getStartedAt().format(DATE_FMT) : "")
                            .time(s.getStartedAt() != null ? s.getStartedAt().format(TIME_FMT) : "")
                            .status("ENDED".equals(s.getStatus()) ? "COMPLETED" : s.getStatus())
                            .detail(detail.length() > 0 ? detail.toString() : null)
                            .interactionCount(ic)
                            .avgCorrectRate(cr > 0 ? Math.round(cr * 10.0) / 10.0 : null)
                            .topConfusion(tc).build());
        }

        // 3. 作业
        for (HomeworkTask t : homeworkTaskMapper.selectByClassId(classId)) {
            weekMap.computeIfAbsent(weekOf(t.getCreatedAt()), k -> new ArrayList<>()).add(
                    WeekItem.builder()
                            .type("homework").icon("📝").typeLabel("作业")
                            .id(t.getId()).title(t.getTaskName())
                            .date(t.getCreatedAt() != null ? t.getCreatedAt().format(DATE_FMT) : "")
                            .time("").status(t.getStatus())
                            .detail(t.getDeadline() != null ? "截止 " + t.getDeadline().format(DATE_FMT) : null).build());
        }

        // 4. 预习
        for (PreviewTask p : previewTaskMapper.findByClassId(classId)) {
            weekMap.computeIfAbsent(weekOf(p.getCreatedAt()), k -> new ArrayList<>()).add(
                    WeekItem.builder()
                            .type("preview").icon("📖").typeLabel("预习")
                            .id(p.getId()).title(p.getTitle())
                            .date(p.getCreatedAt() != null ? p.getCreatedAt().format(DATE_FMT) : "")
                            .time("").status(p.getStatus()).detail(p.getKnowledgePoint()).build());
        }

        // 按周组装（过去4周 + 本周 + 未来4周）
        List<WeekGroup> weeks = new ArrayList<>();
        int thisWeek = LocalDate.now().get(WF.weekOfWeekBasedYear());
        for (int w = thisWeek - 4; w <= thisWeek + 4; w++) {
            List<WeekItem> items = weekMap.getOrDefault(w, new ArrayList<>());
            items.sort(Comparator.comparing(WeekItem::getDate).reversed());
            String label;
            if (w == thisWeek) label = "本周";
            else if (w == thisWeek - 1) label = "上周";
            else if (w == thisWeek + 1) label = "下周";
            else if (w < thisWeek) label = (thisWeek - w) + "周前";
            else label = (w - thisWeek) + "周后";
            weeks.add(WeekGroup.builder().weekNumber(w).label(label).items(items).build());
        }

        return TimelineDTO.builder().weeks(weeks).build();
    }

    private double avgCorrectRate(List<Interaction> interactions) {
        double sum = 0; int count = 0;
        for (Interaction i : interactions) {
            if (i.getCorrectKey() == null) continue;
            List<InteractionResponse> list = responseMapper.findByInteractionId(i.getId());
            if (list.isEmpty()) continue;
            long correct = list.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            sum += (double) correct / list.size() * 100;
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    private String topConfusion(Long sessionId) {
        List<Map<String, Object>> stats = confusionEventMapper.countByKnowledgePoint(sessionId);
        return stats.isEmpty() ? null : (String) stats.get(0).get("name");
    }

    private int weekOf(LocalDateTime dt) {
        return dt != null ? dt.toLocalDate().get(WF.weekOfWeekBasedYear())
                : LocalDate.now().get(WF.weekOfWeekBasedYear());
    }

    private int weekOf(LocalDate dt) {
        return dt != null ? dt.get(WF.weekOfWeekBasedYear())
                : LocalDate.now().get(WF.weekOfWeekBasedYear());
    }
}
