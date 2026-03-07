package com.modernmvn.backend.repository;

import com.modernmvn.backend.entity.CrawlerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlerStateRepository extends JpaRepository<CrawlerStateEntity, Long> {
}
