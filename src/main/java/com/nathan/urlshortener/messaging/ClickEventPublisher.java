package com.nathan.urlshortener.messaging;

import com.nathan.urlshortener.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClickEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ClickEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String shortCode) {
        ClickEvent event = ClickEvent.of(shortCode);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.CLICK_ROUTING_KEY, event);
    }
}
