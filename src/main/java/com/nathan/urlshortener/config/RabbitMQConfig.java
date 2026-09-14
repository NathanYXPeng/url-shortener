package com.nathan.urlshortener.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "url-shortener-exchange";
    public static final String CLICK_QUEUE = "click-events-queue";
    public static final String CLICK_ROUTING_KEY = "click-event";

    public static final String DLX = "url-shortener-dlx";
    public static final String CLICK_DLQ = "click-events-dlq";
    public static final String CLICK_DEAD_ROUTING_KEY = "click-event-dead";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange clickExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    // If a consumer nacks a message (e.g. DB write keeps failing), RabbitMQ
    // routes it here instead of retrying forever, so one bad message can't
    // block the whole queue.
    @Bean
    public Queue clickQueue() {
        return QueueBuilder.durable(CLICK_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", CLICK_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue clickDeadLetterQueue() {
        return QueueBuilder.durable(CLICK_DLQ).build();
    }

    @Bean
    public Binding clickBinding() {
        return BindingBuilder.bind(clickQueue()).to(clickExchange()).with(CLICK_ROUTING_KEY);
    }

    @Bean
    public Binding clickDeadLetterBinding() {
        return BindingBuilder.bind(clickDeadLetterQueue()).to(deadLetterExchange()).with(CLICK_DEAD_ROUTING_KEY);
    }
}
