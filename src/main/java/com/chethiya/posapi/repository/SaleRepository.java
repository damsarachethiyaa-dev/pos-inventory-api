package com.chethiya.posapi.repository;

import com.chethiya.posapi.domain.Sale;
import com.chethiya.posapi.domain.SaleStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Sale> findByReceiptNumber(String receiptNumber);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findByIdWithItems(@Param("id") Long id);

    @Query("""
            SELECT s FROM Sale s
            WHERE s.status = :status
              AND s.createdAt >= :from
              AND s.createdAt < :to
            ORDER BY s.createdAt DESC
            """)
    List<Sale> findByStatusAndCreatedAtBetween(
            @Param("status") SaleStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
