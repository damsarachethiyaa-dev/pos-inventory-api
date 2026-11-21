package com.chethiya.posapi.controller;

import com.chethiya.posapi.dto.ProductDtos.CreateProductRequest;
import com.chethiya.posapi.dto.ProductDtos.ProductResponse;
import com.chethiya.posapi.dto.ProductDtos.RestockRequest;
import com.chethiya.posapi.dto.ProductDtos.UpdateProductRequest;
import com.chethiya.posapi.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products & Inventory", description = "Catalog search, barcode lookup, stock management, and low-stock alerts")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List active products with pagination and optional search/category filter")
    public Page<ProductResponse> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return productService.listProducts(search, category, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by ID")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Instant barcode lookup for POS scanner checkout")
    public ProductResponse getByBarcode(@PathVariable String barcode) {
        return productService.getByBarcode(barcode);
    }

    @GetMapping("/low-stock")
    @Operation(summary = "List active products at or below their reorder threshold")
    public List<ProductResponse> listLowStock() {
        return productService.listLowStock();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new product (ADMIN only)")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update product metadata and pricing (ADMIN only)")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/restock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atomically increment inventory stock quantity (ADMIN only)")
    public ProductResponse restock(@PathVariable Long id, @Valid @RequestBody RestockRequest request) {
        return productService.restock(id, request);
    }
}
