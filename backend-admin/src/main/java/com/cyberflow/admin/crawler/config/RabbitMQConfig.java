package com.cyberflow.admin.crawler.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息队列配置类。
 * <p>
 * 负责定义爬虫任务调度所需的所有交换机（Exchange）、队列（Queue）和绑定关系（Binding）。
 * 任务消息通过 {@link RabbitTemplate} 以 JSON 格式发布到对应的路由键，消费者监听各自队列进行处理。
 * </p>
 *
 * <h3>消息架构</h3>
 * <ul>
 *   <li><b>任务交换机</b>（crawler.tasks）：接收所有爬虫任务消息</li>
 *   <li><b>死信交换机</b>（crawler.tasks.dlx）：处理失败或超时的任务消息</li>
 *   <li><b>任务结果队列</b>（task.result）：接收爬虫执行结果回调</li>
 *   <li><b>死信队列</b>（task.dead）：存放无法处理的消息</li>
 * </ul>
 *
 * @author CyberFlow
 */
@Configuration
public class RabbitMQConfig {

    /** 任务交换机名称，所有爬虫任务消息均发送到该交换机 */
    public static final String EXCHANGE_TASKS = "crawler.tasks";

    /** 死信交换机名称，用于接收处理失败或超时的消息 */
    public static final String EXCHANGE_DLX = "crawler.tasks.dlx";

    /** 站点爬取队列，用于接收站点数据采集任务 */
    public static final String QUEUE_SITE_CRAWL = "site.crawl";

    /** 订单爬取队列，用于接收订单数据采集任务 */
    public static final String QUEUE_ORDER_CRAWL = "order.crawl";

    /** 商品爬取队列，用于接收商品详情采集任务 */
    public static final String QUEUE_PRODUCT_CRAWL = "product.crawl";

    /** 任务结果回调队列，爬虫执行完毕后将结果发送到该队列 */
    public static final String QUEUE_TASK_RESULT = "task.result";

    /** 死信队列，存放处理失败后转发的消息 */
    public static final String QUEUE_TASK_DEAD = "task.dead";

    /** 站点爬取路由键，匹配站点爬取任务消息 */
    public static final String RK_SITE = "crawler.task.site";

    /** 订单爬取路由键，匹配订单爬取任务消息 */
    public static final String RK_ORDER = "crawler.task.order";

    /** 商品爬取路由键，匹配商品爬取任务消息 */
    public static final String RK_PRODUCT = "crawler.task.product";

    /** 结果回调路由键，匹配任务结果消息 */
    public static final String RK_RESULT = "crawler.task.result";

    /** 死信路由键，匹配死信消息 */
    public static final String RK_DEAD = "crawler.task.dead";

    /**
     * 创建任务主题交换机。
     *
     * @return TopicExchange 实例
     */
    @Bean
    public TopicExchange taskExchange() {
        return new TopicExchange(EXCHANGE_TASKS);
    }

    /**
     * 创建死信主题交换机。
     *
     * @return TopicExchange 实例
     */
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(EXCHANGE_DLX);
    }

    /**
     * 创建站点爬取持久化队列，配置死信转发。
     *
     * @return Queue 实例
     */
    @Bean
    public Queue siteCrawlQueue() {
        return QueueBuilder.durable(QUEUE_SITE_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    /**
     * 创建订单爬取持久化队列，配置死信转发。
     *
     * @return Queue 实例
     */
    @Bean
    public Queue orderCrawlQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    /**
     * 创建商品爬取持久化队列，配置死信转发。
     *
     * @return Queue 实例
     */
    @Bean
    public Queue productCrawlQueue() {
        return QueueBuilder.durable(QUEUE_PRODUCT_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    /**
     * 创建任务结果持久化队列。
     *
     * @return Queue 实例
     */
    @Bean
    public Queue taskResultQueue() {
        return new Queue(QUEUE_TASK_RESULT, true);
    }

    /**
     * 创建死信持久化队列。
     *
     * @return Queue 实例
     */
    @Bean
    public Queue taskDeadQueue() {
        return new Queue(QUEUE_TASK_DEAD, true);
    }

    /**
     * 绑定站点爬取队列到任务交换机，路由键为 {@link #RK_SITE}。
     *
     * @return Binding 实例
     */
    @Bean
    public Binding siteBinding() {
        return BindingBuilder.bind(siteCrawlQueue()).to(taskExchange()).with(RK_SITE);
    }

    /**
     * 绑定订单爬取队列到任务交换机，路由键为 {@link #RK_ORDER}。
     *
     * @return Binding 实例
     */
    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderCrawlQueue()).to(taskExchange()).with(RK_ORDER);
    }

    /**
     * 绑定商品爬取队列到任务交换机，路由键为 {@link #RK_PRODUCT}。
     *
     * @return Binding 实例
     */
    @Bean
    public Binding productBinding() {
        return BindingBuilder.bind(productCrawlQueue()).to(taskExchange()).with(RK_PRODUCT);
    }

    /**
     * 绑定结果回调队列到任务交换机，路由键为 {@link #RK_RESULT}。
     *
     * @return Binding 实例
     */
    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(taskResultQueue()).to(taskExchange()).with(RK_RESULT);
    }

    /**
     * 绑定死信队列到死信交换机，路由键为 {@link #RK_DEAD}。
     *
     * @return Binding 实例
     */
    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(taskDeadQueue()).to(dlxExchange()).with(RK_DEAD);
    }

    /**
     * JSON 消息转换器，用于将消息序列化为 JSON 格式传输。
     *
     * @return Jackson2JsonMessageConverter 实例
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate，设置连接工厂和消息转换器。
     *
     * @param connectionFactory RabbitMQ 连接工厂
     * @return RabbitTemplate 实例
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
