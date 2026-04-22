package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.DashboardUploadDTO;
import java.util.Map;

/**
 * 仪表盘数据RAG服务接口
 */
public interface DashboardRagService {
    
    /**
     * 上传仪表盘数据到RAG
     * @param data 仪表盘数据
     * @return 上传结果
     */
    Map<String, Object> uploadDashboard(DashboardUploadDTO data);
}
