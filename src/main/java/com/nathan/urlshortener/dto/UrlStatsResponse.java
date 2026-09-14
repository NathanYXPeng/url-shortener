package com.nathan.urlshortener.dto;

import java.time.LocalDateTime;

public record UrlStatsResponse(
        String shortCode,
        String originalUrl,
        long clickCount,
        LocalDateTime createdAt
) {
}
