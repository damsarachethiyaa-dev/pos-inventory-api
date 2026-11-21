package com.chethiya.posapi.controller;

import com.chethiya.posapi.dto.SaleDtos.CheckoutRequest;
import com.chethiya.posapi.dto.SaleDtos.DailySalesSummaryResponse;
import com.chethiya.posapi.dto.SaleDtos.SaleResponse;
import com.chethiya.posapi.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales")
@Tag(name = "POS Checkout & Sales", description = "Transactional checkout, receipt lookup, void/refund, and daily revenue summary")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    @Operation(summary = "Process a transactional POS sale and atomically deduct product stock")
    public SaleResponse checkout(@Valid @RequestBody CheckoutRequest request, Authentication authentication) {
        return saleService.checkout(request, authentication.getName());
    }

    @GetMapping("/receipt/{receiptNumber}")
    @Operation(summary = "Retrieve a completed or voided sale by receipt number")
    public SaleResponse getByReceipt(@PathVariable String receiptNumber) {
        return saleService.getByReceiptNumber(receiptNumber);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Void a sale and atomically restore deducted inventory (ADMIN only)")
    public SaleResponse voidSale(@PathVariable Long id) {
        return saleService.voidSale(id);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get daily completed sales revenue summary (ADMIN only)")
    public DailySalesSummaryResponse getDailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return saleService.getDailySummary(date);
    }
}
