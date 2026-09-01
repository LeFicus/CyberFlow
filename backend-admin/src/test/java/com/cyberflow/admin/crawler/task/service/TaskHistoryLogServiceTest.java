package com.cyberflow.admin.crawler.task.service;

import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.cursor.Cursor;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskHistoryLogServiceTest {
    @Test
    void mapperAnnotationsParseWithoutBuildingAFullApplicationContext() {
        Configuration configuration = new Configuration();
        configuration.addMapper(TaskHistoryMapper.class);
        assertTrue(configuration.hasMapper(TaskHistoryMapper.class));
    }

    @Test
    void readsAcrossImmutableChunksUsingUnicodeCodePointOffsets() {
        TaskHistoryMapper mapper = mock(TaskHistoryMapper.class);
        TaskHistoryService service = new TaskHistoryService(mapper);
        when(mapper.selectLogMetadata("task-1")).thenReturn(Map.of(
                "taskId", "task-1", "status", "RUNNING", "totalLength", 8L));
        when(mapper.selectLogSegments("task-1", 2, 6)).thenReturn(List.of(
                Map.of("content", "ab🚀c\n", "startOffset", 0L, "endOffset", 5L),
                Map.of("content", "中文\n", "startOffset", 5L, "endOffset", 8L)
        ));

        Map<String, Object> result = service.getLogChunk("task-1", 2, 4);

        assertEquals("🚀c\n中", result.get("chunk"));
        assertEquals(6L, result.get("nextOffset"));
        assertEquals(8L, result.get("totalLength"));
        assertTrue((Boolean) result.get("truncated"));
        assertTrue((Boolean) result.get("hasMore"));
    }

    @Test
    void tailReadsOnlyTheOverlappingChunksAndMarksCompleteTail() {
        TaskHistoryMapper mapper = mock(TaskHistoryMapper.class);
        TaskHistoryService service = new TaskHistoryService(mapper);
        when(mapper.selectLogMetadata("task-2")).thenReturn(Map.of(
                "taskId", "task-2", "status", "SUCCESS", "totalLength", 8L));
        when(mapper.selectLogSegments("task-2", 5, 8)).thenReturn(List.of(
                Map.of("content", "中文\n", "startOffset", 5L, "endOffset", 8L)
        ));

        Map<String, Object> result = service.getLogTail("task-2", 3);

        assertEquals("中文\n", result.get("chunk"));
        assertEquals(8L, result.get("nextOffset"));
        assertTrue((Boolean) result.get("truncated"));
        assertFalse((Boolean) result.get("hasMore"));
        verify(mapper).selectLogSegments("task-2", 5, 8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeDownloadStreamsChunksInMapperOrder() throws Exception {
        TaskHistoryMapper mapper = mock(TaskHistoryMapper.class);
        Cursor<Map<String, Object>> cursor = mock(Cursor.class);
        when(cursor.iterator()).thenReturn(List.of(
                Map.<String, Object>of("content", "first\n"),
                Map.<String, Object>of("content", "第二段🚀\n")
        ).iterator());
        when(mapper.streamLogSegments("task-3")).thenReturn(cursor);
        TaskHistoryService service = new TaskHistoryService(mapper);
        StringWriter writer = new StringWriter();

        service.writeLog("task-3", writer);

        assertEquals("first\n第二段🚀\n", writer.toString());
        verify(cursor).close();
    }
}
