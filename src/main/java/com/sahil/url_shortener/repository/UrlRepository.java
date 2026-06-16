package com.sahil.url_shortener.repository;

import com.sahil.url_shortener.entity.UrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<UrlEntity, Long> {

    Optional<UrlEntity> findByShortCode(String shortCode);

    Optional<UrlEntity> findByLongUrl(String longUrl);

    // Find all URLs that have expired (expiresAt is set and in the past)
    List<UrlEntity> findByExpiresAtBeforeAndExpiresAtIsNotNull(LocalDateTime now);
}