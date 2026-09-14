package com.nathan.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUrlRequest(
        @NotBlank
        @Pattern(regexp = "^https?://.+", message = "originalUrl must start with http:// or https://")
        String originalUrl
) {
}
