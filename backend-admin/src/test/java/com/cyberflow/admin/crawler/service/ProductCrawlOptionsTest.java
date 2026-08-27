package com.cyberflow.admin.crawler.service;

import com.cyberflow.admin.crawler.config.RabbitMQConfig;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCrawlOptionsTest {
    @Test
    void newEnginesDispatchAndUnimplementedEnginesAreRejected() {
        when(publisher.createTaskId()).thenReturn("task");
        for (String engine : List.of("magento", "wix", "ecwid", "shopline")) {
            assertEquals("task", service.triggerProductCrawl(1L, "shop.test", engine, "Tools", "main", 2L).get("task_id"));
            verify(publisher).publishProductCrawl("task", 1L, "shop.test", engine, "Tools", "main", 2L, Map.of());
        }
        clearInvocations(publisher, history);
        assertEquals("Rejected", service.triggerProductCrawl(1L, "shop.test", "opencart", "Tools", "main", 2L).get("status"));
        verifyNoInteractions(publisher, history);
    }

    private final TaskMessagePublisher publisher = mock(TaskMessagePublisher.class);
    private final TaskHistoryService history = mock(TaskHistoryService.class);
    private final CrawlerService service = new CrawlerService(publisher, history,
        mock(CrawlerConfigService.class), mock(CrawlCursorMapper.class));

    @Test
    void preservesExplicitNullAndFalseUntilDispatch() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("max_product_price_usd", null);
        options.put("require_image", false);
        options.put("require_description", false);
        options.put("currency", "AUD");
        when(publisher.createTaskId()).thenReturn("task");
        var result = service.triggerProductCrawl(1L, "shop.test", "bigcommerce", "Tools", "main", 2L, options);
        assertEquals("task", result.get("task_id"));
        verify(publisher).publishProductCrawl("task", 1L, "shop.test", "bigcommerce", "Tools", "main", 2L, options);
    }

    @Test
    void invalidOptionsDoNotCreateTaskOrMessage() {
        List<Map<String, Object>> invalid = List.of(
            Map.of("unknown", true), Map.of("require_image", "false"),
            Map.of("max_product_price_usd", -1), Map.of("max_product_price_usd", Double.NaN),
            Map.of("max_product_price_usd", true), Map.of("currency", "AUD/USD")
        );
        for (var options : invalid) {
            assertThrows(IllegalArgumentException.class, () -> service.triggerProductCrawl(
                1L, "shop.test", "bigcommerce", "Tools", "main", 2L, options));
        }
        verifyNoInteractions(publisher, history);
    }

    @Test
    void olderCallersStillDispatchWithoutOptions() {
        when(publisher.createTaskId()).thenReturn("task");
        service.triggerProductCrawl(1L, "shop.test", "shopify", "Tools", "main", 2L);
        verify(publisher).publishProductCrawl("task", 1L, "shop.test", "shopify", "Tools", "main", 2L, Map.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void messagePayloadPreservesOptionsIncludingNull() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        var messagePublisher = new TaskMessagePublisher(rabbit);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("max_product_price_usd", null);
        options.put("require_image", false);
        messagePublisher.publishProductCrawl("task", 1L, "shop.test", "bigcommerce", "Tools", "main", 2L, options);
        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE_TASKS), eq(RabbitMQConfig.RK_PRODUCT), captured.capture());
        var payload = (Map<String, Object>) ((Map<String, Object>) captured.getValue()).get("payload");
        assertEquals(options, payload.get("crawl_options"));
        assertTrue(((Map<String, Object>) payload.get("crawl_options")).containsKey("max_product_price_usd"));
    }
}
