package com.nathan.urlshortener.dto;

public record CreateUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl
) {
}
