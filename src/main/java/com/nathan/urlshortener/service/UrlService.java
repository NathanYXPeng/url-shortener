package com.nathan.urlshortener.service;

import com.nathan.urlshortener.dto.CreateUrlResponse;
import com.nathan.urlshortener.dto.UrlStatsResponse;
import com.nathan.urlshortener.entity.ShortUrl;
import com.nathan.urlshortener.messaging.ClickEventPublisher;
import com.nathan.urlshortener.repository.ShortUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.NoSuchElementException;

@Service
public class UrlService {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private static final String CACHE_KEY_PREFIX = "url:";

    private final ShortUrlRepository shortUrlRepository;
    private final StringRedisTemplate redisTemplate;
    private final ClickEventPublisher clickEventPublisher;
    private final SecureRandom random = new SecureRandom();

    private final String baseUrl;
    private final long cacheTtlSeconds;

    public UrlService(ShortUrlRepository shortUrlRepository,
                       StringRedisTemplate redisTemplate,
                       ClickEventPublisher clickEventPublisher,
                       @Value("${app.base-url}") String baseUrl,
                       @Value("${app.click-cache-ttl-seconds}") long cacheTtlSeconds) {
        this.shortUrlRepository = shortUrlRepository;
        this.redisTemplate = redisTemplate;
        this.clickEventPublisher = clickEventPublisher;
        this.baseUrl = baseUrl;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public CreateUrlResponse createShortUrl(String originalUrl) {
        String shortCode = generateUniqueShortCode();
        ShortUrl shortUrl = new ShortUrl(shortCode, originalUrl);
        shortUrlRepository.save(shortUrl);

        return new CreateUrlResponse(shortCode, baseUrl + "/" + shortCode, originalUrl);
    }

    // Cache-aside: check Redis first so repeat clicks on a popular link
    // don't all hit Postgres; on a miss, read the DB and warm the cache.
    public String resolveOriginalUrl(String shortCode) {
        String cacheKey = CACHE_KEY_PREFIX + shortCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            publishClickEvent(shortCode);
            return cached;
        }

        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new NoSuchElementException("Short code not found: " + shortCode));

        redisTemplate.opsForValue().set(cacheKey, shortUrl.getOriginalUrl(), Duration.ofSeconds(cacheTtlSeconds));
        publishClickEvent(shortCode);
        return shortUrl.getOriginalUrl();
    }

    public UrlStatsResponse getStats(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new NoSuchElementException("Short code not found: " + shortCode));

        return new UrlStatsResponse(
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getClickCount(),
                shortUrl.getCreatedAt()
        );
    }

    // The redirect response doesn't wait on this - it's fire-and-forget onto
    // the queue so click accounting can't slow down or break the redirect.
    private void publishClickEvent(String shortCode) {
        clickEventPublisher.publish(shortCode);
    }

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!shortUrlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code after "
                + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
