package com.cyberflow.admin.common;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置类。
 * <p>
 * 注册 MyBatis-Plus 拦截器，启用分页查询等功能。
 * 当前配置了 MySQL 数据库的分页方言。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 核心拦截器。
     * <p>
     * 添加 {@link PaginationInnerInterceptor} 以支持基于 MySQL 的物理分页查询。
     * </p>
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
