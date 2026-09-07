package com.slangword.repository;

import com.slangword.domain.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    Page<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);
}
