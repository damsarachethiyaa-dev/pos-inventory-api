package com.chethiya.posapi.dto;

import com.chethiya.posapi.domain.PaymentMethod;
import com.chethiya.posapi.domain.Sale;
import com.chethiya.posapi.domain.SaleItem;
import com.chethiya.posapi.domain.SaleStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SaleDtos {

    private SaleDtos() {
    }

    public record SaleItemRequest(
            @NotNull(message = "Product ID is required") Long productId,
            @Min(value = 1, message = "Quantity must be at least 1") int quantity) {
    }

    public record CheckoutRequest(
            @NotNull(message = "Payment method is required") PaymentMethod paymentMethod,
            @NotEmpty(message = "At least one item is required") List<@Valid SaleItemRequest> items) {
    }

    public record SaleItemResponse(
            Long productId,
            String productSku,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {

        public static SaleItemResponse from(SaleItem item) {
            return new SaleItemResponse(
                    item.getProduct().getId(),
                    item.getProductSku(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getLineTotal());
        }
    }

    public record SaleResponse(
            Long id,
            String receiptNumber,
            String cashierUsername,
            PaymentMethod paymentMethod,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            SaleStatus status,
            Instant createdAt,
            List<SaleItemResponse> items) {

        public static SaleResponse from(Sale sale) {
            return new SaleResponse(
                    sale.getId(),
                    sale.getReceiptNumber(),
                    sale.getCashierUsername(),
                    sale.getPaymentMethod(),
                    sale.getSubtotal(),
                    sale.getTaxAmount(),
                    sale.getTotalAmount(),
                    sale.getStatus(),
                    sale.getCreatedAt(),
                    sale.getItems().stream().map(SaleItemResponse::from).toList());
        }
    }

    public record DailySalesSummaryResponse(
            LocalDate date,
            long completedTransactions,
            BigDecimal grossRevenue,
            BigDecimal totalTax,
            BigDecimal netSubtotal) {
    }
}
