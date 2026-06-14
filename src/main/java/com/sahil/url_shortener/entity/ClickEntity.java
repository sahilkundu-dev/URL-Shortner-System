package com.sahil.url_shortener.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "url_clicks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private UrlEntity urlEntity;

    @Column(nullable = false)
    private LocalDateTime clickedAt;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @PrePersist
    public void prePersist() {
        this.clickedAt = LocalDateTime.now();
    }
}