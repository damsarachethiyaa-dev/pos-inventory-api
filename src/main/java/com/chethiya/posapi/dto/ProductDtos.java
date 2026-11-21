package com.chethiya.posapi.dto;

import com.chethiya.posapi.domain.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 64) String barcode,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 80) String category,
            @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal unitPrice,
            @Min(0) int stockQuantity,
            @Min(0) int reorderLevel) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 80) String category,
            @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal unitPrice,
            @Min(0) int reorderLevel,
            boolean active) {
    }

    public record RestockRequest(
            @Min(value = 1, message = "Restock quantity must be at least 1") int quantity) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String barcode,
            String name,
            String category,
            BigDecimal unitPrice,
            int stockQuantity,
            int reorderLevel,
            boolean lowStock,
            boolean active,
            Instant updatedAt) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(
                    p.getId(),
                    p.getSku(),
                    p.getBarcode(),
                    p.getName(),
                    p.getCategory(),
                    p.getUnitPrice(),
                    p.getStockQuantity(),
                    p.getReorderLevel(),
                    p.getStockQuantity() <= p.getReorderLevel(),
                    p.isActive(),
                    p.getUpdatedAt());
        }
    }
}
