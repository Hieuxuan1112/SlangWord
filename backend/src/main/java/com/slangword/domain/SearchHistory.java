package com.slangword.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "search_history", indexes = @Index(name = "idx_history_user", columnList = "user_id, searched_at"))
@EntityListeners(AuditingEntityListener.class)
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String keyword;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @CreatedDate
    @Column(name = "searched_at", nullable = false, updatable = false)
    private Instant searchedAt;

    protected SearchHistory() {
    }

    public SearchHistory(Long userId, String keyword, int resultCount) {
        this.userId = userId;
        this.keyword = keyword;
        this.resultCount = resultCount;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getKeyword() {
        return keyword;
    }

    public int getResultCount() {
        return resultCount;
    }

    public Instant getSearchedAt() {
        return searchedAt;
    }
}
