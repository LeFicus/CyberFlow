package com.cyberflow.admin.category;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomCategoryServiceTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CustomCategoryService service = new CustomCategoryService(jdbc);
    @SuppressWarnings("unchecked")
    void rows(CustomCategoryService.Category... categories) {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(categories));
    }
    @Test void validatesNames() {
        assertEquals("电钻", CustomCategoryService.validateName(" 电钻 "));
        for (String name : List.of("", "a|||b", "枪支", "x\ny", "a".repeat(101)))
            assertThrows(IllegalArgumentException.class, () -> CustomCategoryService.validateName(name));
    }
    @Test void rejectsDisabledSelectionsAndAllowsUnchangedHistory() {
        rows(new CustomCategoryService.Category(1,0,"工具",false,0),new CustomCategoryService.Category(2,1,"电钻",true,0));
        assertThrows(IllegalArgumentException.class, () -> service.validateSelection("电钻",null));
        assertThrows(IllegalArgumentException.class, () -> service.validateSelection("未知",null));
        assertDoesNotThrow(() -> service.validateSelection("旧分类","旧分类"));
    }
    @Test void rejectsCyclesDeepTreesAndDuplicateNames() {
        rows(new CustomCategoryService.Category(1,0,"工具",true,0),new CustomCategoryService.Category(2,1,"电钻",true,0));
        assertThrows(IllegalArgumentException.class, () -> service.save(1L,new CustomCategoryService.Input(1L,"工具",true,0)));
        assertThrows(IllegalArgumentException.class, () -> service.save(null,new CustomCategoryService.Input(2L,"新分类",true,0)));
        assertThrows(IllegalArgumentException.class, () -> service.save(null,new CustomCategoryService.Input(0L,"工具",true,0)));
        assertThrows(IllegalArgumentException.class, () -> service.delete(1));
    }
    @Test void usedCategoriesCannotBeRenamedOrDeleted() {
        rows(new CustomCategoryService.Category(1,0,"工具",true,0));
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq("工具"),eq("工具"))).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L,new CustomCategoryService.Input(0L,"机械",true,0)));
        assertThrows(IllegalArgumentException.class, () -> service.delete(1));
        assertDoesNotThrow(() -> service.save(1L,new CustomCategoryService.Input(0L,"工具",false,0)));
    }
}
