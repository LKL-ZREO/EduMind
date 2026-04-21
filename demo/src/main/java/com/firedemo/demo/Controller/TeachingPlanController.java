package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.TeachingPlanRequestDTO;
import com.firedemo.demo.config.Result;
import com.firedemo.demo.mapper.HomeworkKnowledgeMapper;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


import java.util.List;

/**
 * 教案生成控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/teaching-plan")
@RequiredArgsConstructor
public class TeachingPlanController {

    private final JwtUtil jwtUtil;
    private final HomeworkKnowledgeMapper homeworkKnowledgeMapper;

    /**
     * 生成教案
     */
    @PostMapping("/generate")
    public Result<String> generatePlan(
            @RequestBody TeachingPlanRequestDTO requestDTO,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        // 模拟 AI 生成教案
        String plan = generateMockPlan(requestDTO);
        
        log.info("生成教案 - 班级ID: {}, 教师ID: {}", requestDTO.getClassId(), userId);
        
        return Result.success(plan);
    }

    /**
     * 获取薄弱知识点列表
     */
    @GetMapping("/weak-points")
    public Result<List<String>> getWeakKnowledgePoints(
            @RequestParam Long classId,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        // 从数据库查询薄弱知识点
        List<String> weakPoints = homeworkKnowledgeMapper.selectWeakKnowledgePoints(classId);
        
        return Result.success(weakPoints);
    }

    // ============ 私有方法 ============

    private String generateMockPlan(TeachingPlanRequestDTO dto) {
        StringBuilder plan = new StringBuilder();
        
        plan.append("<h4>《");
        plan.append(String.join("、", dto.getWeakKnowledgePoints()));
        plan.append("》专项教案</h4>\n\n");
        
        plan.append("<p><strong>教学目标：</strong></p>\n<ul>\n");
        for (String goal : dto.getGoals()) {
            plan.append("  <li>");
            switch (goal) {
                case "basic":
                    plan.append("巩固基础知识，建立扎实理论功底");
                    break;
                case "difficult":
                    plan.append("突破重点难点，深入理解核心概念");
                    break;
                case "extend":
                    plan.append("举一反三，培养知识迁移能力");
                    break;
                case "review":
                    plan.append("查漏补缺，完善知识体系");
                    break;
                default:
                    plan.append(goal);
            }
            plan.append("</li>\n");
        }
        plan.append("</ul>\n\n");
        
        plan.append("<p><strong>教学重点：</strong>");
        plan.append(dto.getWeakKnowledgePoints().get(0));
        plan.append("核心概念与原理</p>\n\n");
        
        plan.append("<p><strong>教学难点：</strong>");
        plan.append(dto.getWeakKnowledgePoints().get(1));
        plan.append("的实际应用与问题解决</p>\n\n");
        
        plan.append("<p><strong>教学方法：</strong></p>\n<ul>\n");
        plan.append("  <li>案例驱动：通过实际项目案例引入知识点</li>\n");
        plan.append("  <li>对比分析：对比易混淆概念的异同</li>\n");
        plan.append("  <li>实践练习：针对性编程练习巩固理解</li>\n");
        plan.append("</ul>\n\n");
        
        plan.append("<p><strong>建议课时：</strong>2课时（90分钟）</p>\n\n");
        
        plan.append("<p><strong>课后作业：</strong></p>\n<ul>\n");
        plan.append("  <li>完成课后练习题 5-10题</li>\n");
        plan.append("  <li>编写相关代码示例并提交</li>\n");
        plan.append("  <li>总结本节课知识点思维导图</li>\n");
        plan.append("</ul>");
        
        return plan.toString();
    }
}
