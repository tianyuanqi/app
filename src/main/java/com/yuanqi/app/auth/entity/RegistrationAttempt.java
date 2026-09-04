package com.yuanqi.app.auth.entity;
import com.baomidou.mybatisplus.annotation.TableId;import com.baomidou.mybatisplus.annotation.TableName;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("registration_attempt") public class RegistrationAttempt{@TableId private String attemptId;private Long flowId;private Long accountId;private String status;private LocalDateTime completedAt;}
