package com.nathan.urlshortener.repository;

import com.nathan.urlshortener.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    // @Modifying queries run a direct UPDATE against the DB, which requires
    // an active transaction - unlike save()/delete(), Spring Data JPA does
    // not wrap custom @Query methods in one automatically.
    @Transactional
    @Modifying
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + 1 WHERE s.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode);
}
