package com.chethiya.posapi.repository;

import com.chethiya.posapi.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND p.stockQuantity <= p.reorderLevel
            ORDER BY p.stockQuantity ASC, p.name ASC
            """)
    List<Product> findLowStockProducts();

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:search IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Product> searchActiveProducts(
            @Param("search") String search,
            @Param("category") String category,
            Pageable pageable);
}
