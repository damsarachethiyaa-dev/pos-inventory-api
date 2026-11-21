package com.chethiya.posapi.service;

import com.chethiya.posapi.domain.Product;
import com.chethiya.posapi.dto.ProductDtos.CreateProductRequest;
import com.chethiya.posapi.dto.ProductDtos.ProductResponse;
import com.chethiya.posapi.dto.ProductDtos.RestockRequest;
import com.chethiya.posapi.dto.ProductDtos.UpdateProductRequest;
import com.chethiya.posapi.exception.DuplicateResourceException;
import com.chethiya.posapi.exception.ResourceNotFoundException;
import com.chethiya.posapi.repository.ProductRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(String search, String category, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();
        return productRepository
                .searchActiveProducts(normalizedSearch, normalizedCategory, pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(String barcode) {
        Product product = productRepository.findByBarcode(barcode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for barcode: " + barcode));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listLowStock() {
        return productRepository.findLowStockProducts().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase();
        String barcode = request.barcode().trim();

        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("Product SKU already exists: " + sku);
        }
        if (productRepository.existsByBarcode(barcode)) {
            throw new DuplicateResourceException("Product barcode already exists: " + barcode);
        }

        Product product = new Product(
                sku,
                barcode,
                request.name().trim(),
                request.category().trim(),
                request.unitPrice(),
                request.stockQuantity(),
                request.reorderLevel());

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        product.updateDetails(
                request.name().trim(),
                request.category().trim(),
                request.unitPrice(),
                request.reorderLevel(),
                request.active());

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse restock(Long id, RestockRequest request) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        product.addStock(request.quantity());
        return ProductResponse.from(productRepository.save(product));
    }
}
