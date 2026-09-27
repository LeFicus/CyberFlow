package com.cyberflow.admin.crawler.health.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** Persistence for site-health targets and their latest observed state. */
@Mapper
public interface SiteHealthMapper {

    @Select("SELECT site_domain, server_name, server_ip, admin_name, user_group, created_at " +
            "FROM site_info WHERE TRIM(COALESCE(site_domain, '')) <> '' ORDER BY server_ip, site_domain")
    List<Map<String, Object>> listTargets();

    @Select("SELECT site_domain, status FROM site_health_status")
    List<Map<String, Object>> listCurrentStates();

    @Delete("DELETE h FROM site_health_status h LEFT JOIN site_info s ON s.site_domain=h.site_domain WHERE s.id IS NULL")
    int deleteOrphans();

    @Insert("""
            INSERT INTO site_health_status (
                site_domain, status, reason, http_status, final_url, server_name, server_ip,
                admin_name, user_group, latency_ms, consecutive_failures, failure_since, checked_at
            ) VALUES (
                #{domain}, #{status}, #{reason}, #{httpStatus}, #{finalUrl}, #{serverName}, #{serverIp},
                #{adminName}, #{userGroup}, #{latencyMs},
                CASE WHEN #{status}='UNHEALTHY' THEN 1 ELSE 0 END,
                CASE WHEN #{status}='UNHEALTHY' THEN #{checkedAt} ELSE NULL END, #{checkedAt}
            )
            ON DUPLICATE KEY UPDATE
                reason=#{reason},
                http_status=#{httpStatus},
                final_url=#{finalUrl},
                server_name=#{serverName},
                server_ip=#{serverIp},
                admin_name=#{adminName},
                user_group=#{userGroup},
                latency_ms=#{latencyMs},
                consecutive_failures=CASE
                    WHEN #{status}='UNHEALTHY' THEN
                        CASE WHEN site_health_status.status='UNHEALTHY'
                            THEN site_health_status.consecutive_failures + 1 ELSE 1 END
                    ELSE 0 END,
                failure_since=CASE
                    WHEN #{status}='UNHEALTHY' THEN
                        CASE WHEN site_health_status.status='UNHEALTHY'
                            THEN site_health_status.failure_since ELSE #{checkedAt} END
                    ELSE NULL END,
                status=#{status},
                checked_at=#{checkedAt}
            """)
    void upsert(@Param("domain") String domain,
                @Param("status") String status,
                @Param("reason") String reason,
                @Param("httpStatus") Integer httpStatus,
                @Param("finalUrl") String finalUrl,
                @Param("serverName") String serverName,
                @Param("serverIp") String serverIp,
                @Param("adminName") String adminName,
                @Param("userGroup") String userGroup,
                @Param("latencyMs") long latencyMs,
                @Param("checkedAt") java.time.LocalDateTime checkedAt);

    @Select("""
            <script>
            SELECT COUNT(*) FROM site_health_status
            <where>
              <if test='status != null and status != ""'>status=#{status}</if>
              <if test='reason != null and reason != ""'>AND reason=#{reason}</if>
              <if test='userGroup != null and userGroup != ""'>AND user_group=#{userGroup}</if>
              <if test='adminName != null and adminName != ""'>AND admin_name=#{adminName}</if>
              <if test='keyword != null and keyword != ""'>AND (site_domain LIKE CONCAT('%',#{keyword},'%') OR server_name LIKE CONCAT('%',#{keyword},'%') OR server_ip LIKE CONCAT('%',#{keyword},'%') OR admin_name LIKE CONCAT('%',#{keyword},'%') OR reason LIKE CONCAT('%',#{keyword},'%'))</if>
              <if test='ownerName != null and ownerName != ""'>AND FIND_IN_SET(admin_name,#{ownerName}) &gt; 0</if>
            </where>
            </script>
            """)
    long count(@Param("status") String status, @Param("reason") String reason,
               @Param("userGroup") String userGroup,
               @Param("adminName") String adminName, @Param("keyword") String keyword,
               @Param("ownerName") String ownerName);

    @Select("""
            <script>
            SELECT site_domain AS siteDomain, status, reason, http_status AS httpStatus,
                   final_url AS finalUrl, server_name AS serverName, server_ip AS serverIp,
                   admin_name AS adminName, user_group AS userGroup, latency_ms AS latencyMs,
                   consecutive_failures AS consecutiveFailures, failure_since AS failureSince,
                   checked_at AS checkedAt, updated_at AS updatedAt
            FROM site_health_status
            <where>
              <if test='status != null and status != ""'>status=#{status}</if>
              <if test='reason != null and reason != ""'>AND reason=#{reason}</if>
              <if test='userGroup != null and userGroup != ""'>AND user_group=#{userGroup}</if>
              <if test='adminName != null and adminName != ""'>AND admin_name=#{adminName}</if>
              <if test='keyword != null and keyword != ""'>AND (site_domain LIKE CONCAT('%',#{keyword},'%') OR server_name LIKE CONCAT('%',#{keyword},'%') OR server_ip LIKE CONCAT('%',#{keyword},'%') OR admin_name LIKE CONCAT('%',#{keyword},'%') OR reason LIKE CONCAT('%',#{keyword},'%'))</if>
              <if test='ownerName != null and ownerName != ""'>AND FIND_IN_SET(admin_name,#{ownerName}) &gt; 0</if>
            </where>
            ORDER BY CASE status WHEN 'UNHEALTHY' THEN 0 ELSE 1 END, checked_at DESC, site_domain
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> list(@Param("status") String status, @Param("reason") String reason,
                                   @Param("userGroup") String userGroup,
                                   @Param("adminName") String adminName, @Param("keyword") String keyword,
                                   @Param("ownerName") String ownerName, @Param("offset") int offset,
                                   @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(status='HEALTHY'),0) AS healthy,
                   COALESCE(SUM(status='UNHEALTHY'),0) AS unhealthy,
                   MAX(checked_at) AS lastCheckedAt
            FROM site_health_status
            <where>
              <if test='status != null and status != ""'>status=#{status}</if>
              <if test='reason != null and reason != ""'>AND reason=#{reason}</if>
              <if test='userGroup != null and userGroup != ""'>AND user_group=#{userGroup}</if>
              <if test='adminName != null and adminName != ""'>AND admin_name=#{adminName}</if>
              <if test='keyword != null and keyword != ""'>AND (site_domain LIKE CONCAT('%',#{keyword},'%') OR server_name LIKE CONCAT('%',#{keyword},'%') OR server_ip LIKE CONCAT('%',#{keyword},'%') OR admin_name LIKE CONCAT('%',#{keyword},'%') OR reason LIKE CONCAT('%',#{keyword},'%'))</if>
              <if test='ownerName != null and ownerName != ""'>AND FIND_IN_SET(admin_name,#{ownerName}) &gt; 0</if>
            </where>
            </script>
            """)
    Map<String, Object> summary(@Param("status") String status, @Param("reason") String reason,
                                @Param("userGroup") String userGroup,
                                @Param("adminName") String adminName, @Param("keyword") String keyword,
                                @Param("ownerName") String ownerName);

    @Select("""
            <script>
            SELECT DISTINCT user_group FROM site_health_status
            WHERE TRIM(COALESCE(user_group,'')) &lt;&gt; ''
            <if test='ownerName != null and ownerName != ""'>AND FIND_IN_SET(admin_name,#{ownerName}) &gt; 0</if>
            ORDER BY user_group
            </script>
            """)
    List<String> listUserGroups(@Param("ownerName") String ownerName);

    @Select("""
            <script>
            SELECT DISTINCT admin_name FROM site_health_status
            WHERE TRIM(COALESCE(admin_name,'')) &lt;&gt; ''
            <if test='ownerName != null and ownerName != ""'>AND FIND_IN_SET(admin_name,#{ownerName}) &gt; 0</if>
            ORDER BY admin_name
            </script>
            """)
    List<String> listAdminNames(@Param("ownerName") String ownerName);

    @Select("""
            <script>
            SELECT DISTINCT reason FROM site_health_status
            WHERE TRIM(COALESCE(reason,'')) &lt;&gt; ''
            <if test='ownerName != null and ownerName != ""'>AND FIND_IN_SET(admin_name,#{ownerName}) &gt; 0</if>
            ORDER BY reason
            </script>
            """)
    List<String> listReasons(@Param("ownerName") String ownerName);

    @Select("SELECT COUNT(*) FROM site_health_status WHERE status='UNHEALTHY' AND admin_name=#{adminName}")
    long countUnhealthyByAdmin(@Param("adminName") String adminName);

    @Select("SELECT site_domain, reason, server_name, consecutive_failures, failure_since, checked_at " +
            "FROM site_health_status WHERE status='UNHEALTHY' AND admin_name=#{adminName} " +
            "ORDER BY failure_since ASC, checked_at DESC, site_domain LIMIT #{limit}")
    List<Map<String, Object>> listUnhealthyByAdmin(@Param("adminName") String adminName,
                                                    @Param("limit") int limit);
}
