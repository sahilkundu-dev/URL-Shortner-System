package com.sahil.url_shortener.repository;

import com.sahil.url_shortener.entity.ClickEntity;
import com.sahil.url_shortener.entity.UrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClickRepository extends JpaRepository<ClickEntity, Long> {

    long countByUrlEntity(UrlEntity urlEntity);

    List<ClickEntity> findTop10ByUrlEntityOrderByClickedAtDesc(UrlEntity urlEntity);

    void deleteByUrlEntity(UrlEntity urlEntity);
}