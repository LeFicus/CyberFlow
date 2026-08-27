package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.GlobalExceptionHandler;
import com.cyberflow.admin.dashboard.controller.ProductWorkspaceController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductDownloadControllerTest {
    @TempDir Path directory;
    @Test void downloadStreamsFileWithAttachmentHeadersAndRejectsInvalidTickets() throws Exception {
        var service = mock(ProductExportJobService.class);
        var controller = new ProductWorkspaceController(mock(ProductQueryService.class), service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        Path file = directory.resolve("example.zip"); byte[] bytes = {80,75,3,4}; Files.write(file, bytes);
        when(service.consumeTicket("valid")).thenReturn(file);
        when(service.consumeTicket("expired")).thenThrow(new AccessDeniedException("下载链接无效或已过期"));
        mvc.perform(get("/admin/dashboard/product-exports/download").param("ticket", "valid"))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Length", "4"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=products-example.zip"));
        mvc.perform(get("/admin/dashboard/product-exports/download").param("ticket", "expired"))
                .andExpect(status().isForbidden());
    }
}
