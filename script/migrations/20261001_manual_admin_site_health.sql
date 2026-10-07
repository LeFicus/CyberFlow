-- Site health checks are launched manually by administrators only.
DELETE FROM crawler_schedule_config WHERE task_type = 'site_health';

DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
WHERE rm.menu_id = 77 AND r.role_code <> 'ROLE_ADMIN';
