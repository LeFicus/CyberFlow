ALTER TABLE sys_user
    ADD COLUMN site_health_notice_date DATE
        COMMENT '最近一次个人站点异常登录提醒日期（北京时间）' AFTER status;
