package com.sahil.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClickResponse {

    private String shortCode;
    private String longUrl;
    private long totalClicks;
    private List<ClickDetail> recentClicks;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClickDetail {
        private LocalDateTime clickedAt;
        private String ipAddress;
        private String userAgent;
    }
}