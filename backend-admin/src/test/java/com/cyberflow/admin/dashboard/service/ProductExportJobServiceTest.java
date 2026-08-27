package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.ProductQueryMapper;
import com.cyberflow.admin.dashboard.model.ProductFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

class ProductExportJobServiceTest {
    @TempDir Path directory;
    ProductQueryMapper mapper;
    ProductQueryService queries;
    ProductExportJobService service;
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @BeforeEach void setUp() throws Exception {
        mapper = mock(ProductQueryMapper.class); queries = mock(ProductQueryService.class);
        when(queries.username()).thenReturn("alice"); when(queries.snapshot(null)).thenReturn(100L);
        when(queries.allowedDomains("alice")).thenReturn(null);
        service = new ProductExportJobService(mapper, queries, json, directory.toString()); service.initialize();
    }
    @AfterEach void tearDown() { service.shutdown(); }
    private String create(int maxRows) throws Exception {
        return (String)service.create(new ProductFilter(), "csv", maxRows, 1000, null).get("id");
    }
    private void completed(String id) { await().atMost(5, TimeUnit.SECONDS).until(() -> service.get(id).get("state").equals("completed")); }

    @Test void capsRowsExplicitlyAndTicketsAreSingleUse() throws Exception {
        when(mapper.exportBatch(any(), isNull(), eq(100L), eq(0L), eq(3)))
                .thenReturn(List.of(Map.of("id",1L), Map.of("id",2L), Map.of("id",3L)));
        String id = create(2); completed(id);
        assertEquals(2L, service.get(id).get("processed")); assertEquals(true, service.get(id).get("limited"));
        String token = service.ticket(id).get("url").split("ticket=")[1];
        assertTrue(Files.isRegularFile(service.consumeTicket(token)));
        assertThrows(AccessDeniedException.class, () -> service.consumeTicket(token));
        when(queries.username()).thenReturn("bob");
        assertThrows(AccessDeniedException.class, () -> service.get(id)); assertTrue(service.list().isEmpty());
    }
    @Test void completedJobsSurviveRestartAndChangedPermissionsBlockDownload() throws Exception {
        when(mapper.exportBatch(any(), isNull(), anyLong(), anyLong(), anyInt())).thenReturn(List.of());
        String id = create(10); completed(id);
        service.shutdown(); service = new ProductExportJobService(mapper, queries, json, directory.toString()); service.initialize();
        assertEquals("completed", service.get(id).get("state"));
        String token = service.ticket(id).get("url").split("ticket=")[1];
        when(queries.allowedDomains("alice")).thenThrow(new AccessDeniedException("revoked"));
        assertThrows(AccessDeniedException.class, () -> service.consumeTicket(token));
    }
    @Test void cancellationDoesNotPublishPartialFilesAndDuplicateJobsAreRejected() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(mapper.exportBatch(any(), isNull(), anyLong(), anyLong(), anyInt())).thenAnswer(invocation -> {
            entered.countDown(); release.await(3, TimeUnit.SECONDS); return List.of(Map.of("id",1L));
        });
        String id = create(10); assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertThrows(IllegalArgumentException.class, () -> create(10));
        service.cancel(id); release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !Files.exists(directory.resolve(id + ".part")));
        assertEquals("cancelled", service.get(id).get("state")); assertFalse(Files.exists(directory.resolve(id + ".zip")));
        assertThrows(CancellationException.class, () -> service.ticket(id));
    }
    @Test void interruptedJobsAreMarkedFailedAfterRestart() throws Exception {
        var job = new ProductExportJobService.Job(); job.setId(UUID.randomUUID().toString()); job.setUsername("alice");
        job.setState("running"); job.setFormat("csv"); job.setFilters(new ProductFilter()); job.setExpiresAt(System.currentTimeMillis() + 100000);
        json.writeValue(directory.resolve(job.getId() + ".json").toFile(), job);
        service.shutdown(); service = new ProductExportJobService(mapper, queries, json, directory.toString()); service.initialize();
        assertEquals("failed", service.get(job.getId()).get("state"));
    }
}
