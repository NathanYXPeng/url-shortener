package com.nathan.urlshortener.service;

import com.nathan.urlshortener.dto.CreateUrlResponse;
import com.nathan.urlshortener.entity.ShortUrl;
import com.nathan.urlshortener.messaging.ClickEventPublisher;
import com.nathan.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ClickEventPublisher clickEventPublisher;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(shortUrlRepository, redisTemplate, clickEventPublisher,
                "http://localhost:8080", 3600L);
    }

    @Test
    void createShortUrl_savesEntityAndReturnsShortUrl() {
        when(shortUrlRepository.existsByShortCode(anyString())).thenReturn(false);

        CreateUrlResponse response = urlService.createShortUrl("https://example.com/very/long/path");

        assertThat(response.originalUrl()).isEqualTo("https://example.com/very/long/path");
        assertThat(response.shortCode()).hasSize(7);
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/" + response.shortCode());
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void resolveOriginalUrl_cacheHit_doesNotQueryDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abc1234")).thenReturn("https://cached.example.com");

        String result = urlService.resolveOriginalUrl("abc1234");

        assertThat(result).isEqualTo("https://cached.example.com");
        verify(shortUrlRepository, org.mockito.Mockito.never()).findByShortCode(anyString());
        verify(clickEventPublisher).publish("abc1234");
    }

    @Test
    void resolveOriginalUrl_cacheMiss_fallsBackToDatabaseAndWarmsCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:xyz9999")).thenReturn(null);
        ShortUrl entity = new ShortUrl("xyz9999", "https://db.example.com");
        when(shortUrlRepository.findByShortCode("xyz9999")).thenReturn(Optional.of(entity));

        String result = urlService.resolveOriginalUrl("xyz9999");

        assertThat(result).isEqualTo("https://db.example.com");
        verify(valueOperations).set(eq("url:xyz9999"), eq("https://db.example.com"), any());
        verify(clickEventPublisher).publish("xyz9999");
    }

    @Test
    void resolveOriginalUrl_unknownCode_throwsNoSuchElement() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolveOriginalUrl("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
