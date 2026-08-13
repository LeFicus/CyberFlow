package com.cyberflow.admin.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyberflow.admin.system.entity.SysOperationLog;
import com.cyberflow.admin.system.mapper.SysOperationLogMapper;
import org.springframework.stereotype.Service;

/**
 * 系统操作日志业务服务。
 * <p>
 * 继承 MyBatis-Plus 的 {@link ServiceImpl}，提供操作日志的基础 CRUD 操作。
 * 操作日志由 {@code OperationLogAspect} 切面自动采集并调用 save 方法入库。
 * </p>
 *
 * @author CyberFlow Team
 * @see com.cyberflow.admin.common.OperationLogAspect
 * @since 1.0.0
 */
@Service
public class SysOperationLogService extends ServiceImpl<SysOperationLogMapper, SysOperationLog> {
}
