package com.chethiya.posapi.service;

import com.chethiya.posapi.domain.Product;
import com.chethiya.posapi.domain.Sale;
import com.chethiya.posapi.domain.SaleItem;
import com.chethiya.posapi.domain.SaleStatus;
import com.chethiya.posapi.dto.SaleDtos.CheckoutRequest;
import com.chethiya.posapi.dto.SaleDtos.DailySalesSummaryResponse;
import com.chethiya.posapi.dto.SaleDtos.SaleItemRequest;
import com.chethiya.posapi.dto.SaleDtos.SaleResponse;
import com.chethiya.posapi.exception.InsufficientStockException;
import com.chethiya.posapi.exception.ResourceNotFoundException;
import com.chethiya.posapi.repository.ProductRepository;
import com.chethiya.posapi.repository.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final BigDecimal taxRate;

    public SaleService(
            ProductRepository productRepository,
            SaleRepository saleRepository,
            @Value("${app.tax-rate:0.08}") BigDecimal taxRate) {
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.taxRate = taxRate;
    }

    @Transactional
    public SaleResponse checkout(CheckoutRequest request, String cashierUsername) {
        // Merge duplicate productIds and lock rows in deterministic ID order to prevent deadlocks
        Map<Long, Integer> requestedQuantities = new LinkedHashMap<>();
        for (SaleItemRequest item : request.items()) {
            requestedQuantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        List<Long> sortedProductIds = requestedQuantities.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<SaleItem> lineItems = new ArrayList<>(sortedProductIds.size());

        for (Long productId : sortedProductIds) {
            int qty = requestedQuantities.get(productId);
            Product product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

            if (!product.isActive()) {
                throw new IllegalStateException("Product is inactive and cannot be sold: " + product.getSku());
            }
            if (product.getStockQuantity() < qty) {
                throw new InsufficientStockException(
                        "Insufficient stock for %s (SKU: %s): requested %d, available %d"
                                .formatted(product.getName(), product.getSku(), qty, product.getStockQuantity()));
            }

            product.deductStock(qty);
            BigDecimal lineTotal = product.getUnitPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            lineItems.add(new SaleItem(product, qty, lineTotal));
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        String receiptNumber = generateReceiptNumber();
        Sale sale = new Sale(
                receiptNumber,
                cashierUsername,
                request.paymentMethod(),
                subtotal,
                taxAmount,
                totalAmount);
        lineItems.forEach(sale::addItem);

        Sale saved = saleRepository.save(sale);
        return SaleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SaleResponse getByReceiptNumber(String receiptNumber) {
        Sale sale = saleRepository.findByReceiptNumber(receiptNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found for receipt: " + receiptNumber));
        return SaleResponse.from(sale);
    }

    @Transactional
    public SaleResponse voidSale(Long saleId) {
        Sale sale = saleRepository.findByIdWithItems(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + saleId));

        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new IllegalStateException("Sale is already voided: " + sale.getReceiptNumber());
        }

        List<SaleItem> sortedItems = sale.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        for (SaleItem item : sortedItems) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found with ID: " + item.getProduct().getId()));
            product.addStock(item.getQuantity());
        }

        sale.markVoided();
        return SaleResponse.from(saleRepository.save(sale));
    }

    @Transactional(readOnly = true)
    public DailySalesSummaryResponse getDailySummary(LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(ZoneOffset.UTC);
        Instant startOfDay = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = targetDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Sale> sales = saleRepository.findByStatusAndCreatedAtBetween(
                SaleStatus.COMPLETED, startOfDay, endOfDay);

        BigDecimal grossRevenue = sales.stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTax = sales.stream()
                .map(Sale::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netSubtotal = sales.stream()
                .map(Sale::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new DailySalesSummaryResponse(targetDate, sales.size(), grossRevenue, totalTax, netSubtotal);
    }

    private String generateReceiptNumber() {
        String datePart = RECEIPT_DATE.format(Instant.now());
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "RCP-" + datePart + "-" + shortId;
    }
}
