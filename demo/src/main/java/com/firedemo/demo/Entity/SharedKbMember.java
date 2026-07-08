package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("shared_kb_member")
public class SharedKbMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long userId;
    private String role;
    private LocalDateTime joinedAt;
}
