package com.cyberflow.admin.crawler.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 爬取光标实体类，对应数据表 crawl_cursor。
 * <p>
 * 用于记录增量爬取的断点位置，每条记录存储一种爬取类型
 * （如 site_crawler、order_crawler 等）的当前光标值。
 * 每次爬虫任务完成后，由结果消费者更新光标值，
 * 下次任务启动时从此光标继续，实现增量爬取。
 * </p>
 *
 * @author CyberFlow
 */
@Data
@TableName("crawl_cursor")
public class CrawlCursor {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 光标键名，标识爬取类型：site_crawler、site_index_crawler、order_crawler 等 */
    private String cursorKey;

    /** 当前光标值，内容因类型而异（如时间戳字符串或订单 ID 字符串） */
    private String cursorValue;

    /** 上一次同步时间，即最近一次更新光标值的时间 */
    private LocalDateTime lastSyncAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}
