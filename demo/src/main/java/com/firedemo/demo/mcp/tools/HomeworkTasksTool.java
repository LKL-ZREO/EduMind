package com.firedemo.demo.mcp.tools;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.Service.HomeworkTaskService;
import com.firedemo.demo.mcp.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具：班级作业任务列表查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeworkTasksTool implements ToolDefinition {

    private final ClassService classService;
    private final HomeworkTaskService homeworkTaskService;

    @Override
    public String name() {
        return "queryHomeworkTasks";
    }

    @Override
    public String description() {
        return "查询班级的作业任务列表，包括作业名称、描述、截止时间、状态";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "className", Map.of("type", "string", "description", "班级名称，如 C101 班")
                ),
                "required", List.of("className")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String className = (String) arguments.get("className");
        log.info("MCP Tool queryHomeworkTasks: className={}", className);

        try {
            ClassInfo classInfo = classService.getClassByName(className);
            if (classInfo == null) {
                return "未找到班级「" + className + "」，请确认班级名称是否正确。";
            }
            Long classId = classInfo.getId();

            List<HomeworkTask> tasks = homeworkTaskService.listByClassId(classId);
            if (tasks == null || tasks.isEmpty()) {
                return "班级「" + className + "」暂无作业任务。";
            }

            long activeCount = tasks.stream().filter(t -> !"closed".equals(t.getStatus())).count();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("班级「%s」共 %d 个作业（进行中 %d 个）：\n",
                    className, tasks.size(), activeCount));

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            for (HomeworkTask task : tasks) {
                String status = "closed".equals(task.getStatus()) ? "❌ 已关闭" : "✅ 进行中";
                String deadline = task.getDeadline() != null
                        ? task.getDeadline().format(fmt) : "未设置";
                sb.append(String.format("- %s | 截止: %s | %s\n",
                        task.getTaskName(), deadline, status));
                if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                    sb.append("  ").append(task.getDescription()).append("\n");
                }
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("查询作业任务失败", e);
            return "查询失败: " + e.getMessage();
        }
    }
}
