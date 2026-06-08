package com.cyberflow.admin.crawler.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_TASKS = "crawler.tasks";
    public static final String EXCHANGE_DLX = "crawler.tasks.dlx";

    public static final String QUEUE_SITE_CRAWL = "site.crawl";
    public static final String QUEUE_ORDER_CRAWL = "order.crawl";
    public static final String QUEUE_PRODUCT_CRAWL = "product.crawl";
    public static final String QUEUE_TASK_RESULT = "task.result";
    public static final String QUEUE_TASK_DEAD = "task.dead";

    public static final String RK_SITE = "crawler.task.site";
    public static final String RK_ORDER = "crawler.task.order";
    public static final String RK_PRODUCT = "crawler.task.product";
    public static final String RK_RESULT = "crawler.task.result";
    public static final String RK_DEAD = "crawler.task.dead";

    @Bean
    public TopicExchange taskExchange() {
        return new TopicExchange(EXCHANGE_TASKS);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(EXCHANGE_DLX);
    }

    @Bean
    public Queue siteCrawlQueue() {
        return QueueBuilder.durable(QUEUE_SITE_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    @Bean
    public Queue orderCrawlQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    @Bean
    public Queue productCrawlQueue() {
        return QueueBuilder.durable(QUEUE_PRODUCT_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    @Bean
    public Queue taskResultQueue() {
        return new Queue(QUEUE_TASK_RESULT, true);
    }

    @Bean
    public Queue taskDeadQueue() {
        return new Queue(QUEUE_TASK_DEAD, true);
    }

    @Bean
    public Binding siteBinding() {
        return BindingBuilder.bind(siteCrawlQueue()).to(taskExchange()).with(RK_SITE);
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderCrawlQueue()).to(taskExchange()).with(RK_ORDER);
    }

    @Bean
    public Binding productBinding() {
        return BindingBuilder.bind(productCrawlQueue()).to(taskExchange()).with(RK_PRODUCT);
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(taskResultQueue()).to(taskExchange()).with(RK_RESULT);
    }

    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(taskDeadQueue()).to(dlxExchange()).with(RK_DEAD);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
