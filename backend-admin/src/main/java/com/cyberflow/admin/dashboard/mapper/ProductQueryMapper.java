package com.cyberflow.admin.dashboard.mapper;

import com.cyberflow.admin.dashboard.model.ProductFilter;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductQueryMapper {
    @Select("SELECT COALESCE(MAX(id),0) FROM scraped_data.ecommerce_products")
    long maxId();

    @Options(timeout = 20)
    List<Map<String, Object>> search(@Param("f") ProductFilter filter, @Param("allowed") List<String> allowed,
            @Param("snapshot") long snapshot, @Param("before") Long before, @Param("limit") int limit);
    @Options(timeout = 10)
    long count(@Param("f") ProductFilter filter, @Param("allowed") List<String> allowed, @Param("snapshot") long snapshot);
    @Options(timeout = 30)
    List<Map<String, Object>> exportBatch(@Param("f") ProductFilter filter, @Param("allowed") List<String> allowed,
            @Param("snapshot") long snapshot, @Param("after") long after, @Param("limit") int limit);
    @Options(timeout = 5)
    List<String> domainOptions(@Param("pattern") String pattern, @Param("allowed") List<String> allowed);

    @Select("<script>SELECT DISTINCT site_domain FROM site_info WHERE admin_name IN " +
            "<foreach collection='owners' item='owner' open='(' close=')' separator=','>#{owner}</foreach></script>")
    List<String> scopeDomains(@Param("owners") List<String> owners);
}
