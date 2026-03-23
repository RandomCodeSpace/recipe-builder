package com.graphrag.jpa;

import com.graphrag.entity.RecipeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecipeJpaRepository extends JpaRepository<RecipeEntity, Long> {
    List<RecipeEntity> findBySourceVersion(String version);
    List<RecipeEntity> findByTraceId(String traceId);
}
