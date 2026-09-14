package com.nathan.urlshortener.messaging;

import com.nathan.urlshortener.config.RabbitMQConfig;
import com.nathan.urlshortener.repository.ShortUrlRepository;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
public class ClickEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClickEventListener.class);
    private static final String DEDUP_KEY_PREFIX = "click:processed:";

    private final ShortUrlRepository shortUrlRepository;
    private final StringRedisTemplate redisTemplate;

    public ClickEventListener(ShortUrlRepository shortUrlRepository, StringRedisTemplate redisTemplate) {
        this.shortUrlRepository = shortUrlRepository;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.CLICK_QUEUE)
    public void handleClickEvent(ClickEvent event, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            // SETNX-style check: if this eventId was already marked processed,
            // this is a redelivery (consumer crashed after DB write but before
            // ack, or the message was requeued) - skip to avoid double counting.
            String dedupKey = DEDUP_KEY_PREFIX + event.eventId();
            Boolean firstTimeSeen = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofHours(24));

            if (Boolean.TRUE.equals(firstTimeSeen)) {
                shortUrlRepository.incrementClickCount(event.shortCode());
            } else {
                log.info("Duplicate click event {} for {}, skipping", event.eventId(), event.shortCode());
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Failed to process click event {}", event.eventId(), ex);
            // requeue=false: don't retry forever, let it go to the dead-letter queue
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
