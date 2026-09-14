package com.nathan.urlshortener.messaging;

import java.time.LocalDateTime;
import java.util.UUID;

// eventId lets the consumer detect redelivery (RabbitMQ gives at-least-once
// delivery, so the same event can arrive twice) and skip double-counting.
public record ClickEvent(
        UUID eventId,
        String shortCode,
        LocalDateTime clickedAt
) {
    public static ClickEvent of(String shortCode) {
        return new ClickEvent(UUID.randomUUID(), shortCode, LocalDateTime.now());
    }
}
