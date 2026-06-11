package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.DTO.DashboardUploadDTO;
import com.firedemo.demo.Service.DashboardRagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 仪表盘 RAG 服务 — 已禁用
 * <p>
 * 仪表盘数据（学情统计、知识掌握度）是结构化数据，应通过 MCP 工具直接从业务表查询，
 * 不应将统计数字分块 + Embedding 化后混入知识库，以免污染 RAG 检索结果。
 * <p>
 * 保留此接口用于向后兼容，调用直接返回空结果。
 */
@Slf4j
@Service
public class DashboardRagServiceImpl implements DashboardRagService {

    @Override
    public Map<String, Object> uploadDashboard(DashboardUploadDTO data) {
        log.info("仪表盘 RAG 上传已禁用，数据应通过 MCP 工具直接查询（classId={}）", data.getClassId());
        Map<String, Object> result = new HashMap<>();
        result.put("skipped", true);
        result.put("message", "仪表盘数据不应存入知识库，请通过 MCP 工具查询");
        return result;
    }
}
