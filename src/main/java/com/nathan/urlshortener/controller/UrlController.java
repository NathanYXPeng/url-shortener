package com.nathan.urlshortener.controller;

import com.nathan.urlshortener.dto.CreateUrlRequest;
import com.nathan.urlshortener.dto.CreateUrlResponse;
import com.nathan.urlshortener.dto.UrlStatsResponse;
import com.nathan.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "URL Shortener")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @Operation(summary = "Create a short URL for a given long URL")
    @PostMapping("/api/urls")
    public ResponseEntity<CreateUrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        CreateUrlResponse response = urlService.createShortUrl(request.originalUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Redirect to the original URL and record a click event")
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlService.resolveOriginalUrl(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    @Operation(summary = "Get click stats for a short URL")
    @GetMapping("/api/urls/{shortCode}/stats")
    public ResponseEntity<UrlStatsResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.getStats(shortCode));
    }
}
