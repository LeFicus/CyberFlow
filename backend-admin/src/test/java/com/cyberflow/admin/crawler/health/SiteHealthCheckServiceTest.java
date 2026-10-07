package com.cyberflow.admin.crawler.health;

import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.crawler.health.mapper.SiteHealthMapper;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import com.cyberflow.admin.system.service.SysNotificationService;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SiteHealthCheckServiceTest {

    @Test
    void triggerCreatesOnlyManualTask() {
        TaskHistoryService historyService = mock(TaskHistoryService.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        SiteHealthCheckService service = new SiteHealthCheckService(
                mock(SiteHealthMapper.class), historyService, mock(CrawlerConfigService.class),
                mock(SysNotificationService.class), executor, mock(DataScopeService.class));

        service.trigger();

        ArgumentCaptor<TaskHistory> savedTask = ArgumentCaptor.forClass(TaskHistory.class);
        verify(historyService).save(savedTask.capture());
        assertEquals("site_health", savedTask.getValue().getType());
        assertEquals("manual", savedTask.getValue().getTriggerType());
        verify(executor).execute(any());
    }

    @Test
    void normalizesConfiguredDomainsWithoutAcceptingPathsOrInvalidHosts() {
        assertEquals("example.com", SiteHealthCheckService.normalizeDomain(" HTTPS://Example.COM/shop "));
        assertEquals("www.example.com", SiteHealthCheckService.normalizeDomain("www.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> SiteHealthCheckService.normalizeDomain("http://localhost/admin"));
        assertThrows(IllegalArgumentException.class,
                () -> SiteHealthCheckService.normalizeDomain("not a domain"));
    }

    @Test
    void healthStatusUpsertClosesValuesClauseBeforeDuplicateKeyUpdate() throws Exception {
        Method method = SiteHealthMapper.class.getMethod("upsert", String.class, String.class, String.class,
                Integer.class, String.class, String.class, String.class, String.class, String.class,
                long.class, java.time.LocalDateTime.class);
        String sql = String.join(" ", method.getAnnotation(Insert.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("#{checkedAt} ) ON DUPLICATE KEY UPDATE"));
        assertFalse(sql.contains("#{checkedAt} ON DUPLICATE KEY UPDATE"));
        assertFalse(sql.contains("VALUES(status)"));
    }

    @Test
    void healthListMapsDisplayFieldsAndEnforcesOwnerScope() throws Exception {
        Method method = SiteHealthMapper.class.getMethod("list", String.class, String.class, String.class,
                String.class, String.class, String.class, int.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("site_domain AS siteDomain"));
        assertTrue(sql.contains("consecutive_failures AS consecutiveFailures"));
        assertTrue(sql.contains("admin_name=#{adminName}"));
        assertTrue(sql.contains("FIND_IN_SET(admin_name,#{ownerName})"));
        assertTrue(sql.contains("reason=#{reason}"));
        assertTrue(sql.contains("reason LIKE"));
    }

    @Test
    void resultFilterAppliesToListCountAndSummaryWithoutBypassingOwnerScope() throws Exception {
        Method[] methods = {
                SiteHealthMapper.class.getMethod("count", String.class, String.class, String.class,
                        String.class, String.class, String.class),
                SiteHealthMapper.class.getMethod("list", String.class, String.class, String.class,
                        String.class, String.class, String.class, int.class, int.class),
                SiteHealthMapper.class.getMethod("summary", String.class, String.class, String.class,
                        String.class, String.class, String.class),
                SiteHealthMapper.class.getMethod("listReasons", String.class)
        };
        for (Method method : methods) {
            String sql = String.join(" ", method.getAnnotation(Select.class).value());
            assertTrue(sql.contains("FIND_IN_SET(admin_name,#{ownerName})"), method.getName());
            if (!method.getName().equals("listReasons")) {
                assertTrue(sql.contains("reason=#{reason}"), method.getName());
            }
        }
    }
}
