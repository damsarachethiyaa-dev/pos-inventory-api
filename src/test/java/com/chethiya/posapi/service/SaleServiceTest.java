package com.chethiya.posapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chethiya.posapi.domain.PaymentMethod;
import com.chethiya.posapi.domain.Product;
import com.chethiya.posapi.domain.Sale;
import com.chethiya.posapi.domain.SaleItem;
import com.chethiya.posapi.domain.SaleStatus;
import com.chethiya.posapi.dto.SaleDtos.CheckoutRequest;
import com.chethiya.posapi.dto.SaleDtos.SaleItemRequest;
import com.chethiya.posapi.dto.SaleDtos.SaleResponse;
import com.chethiya.posapi.exception.InsufficientStockException;
import com.chethiya.posapi.repository.ProductRepository;
import com.chethiya.posapi.repository.SaleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleRepository saleRepository;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleService(productRepository, saleRepository, new BigDecimal("0.08"));
    }

    @Test
    void checkoutDeductsStockAndComputesTotals() {
        Product coffee = new Product("SKU-1", "111", "Coffee", "Drinks", new BigDecimal("100.00"), 10, 2);
        ReflectionTestUtils.setField(coffee, "id", 1L);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(coffee));
        when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 42L);
            return s;
        });

        CheckoutRequest req = new CheckoutRequest(PaymentMethod.CARD, List.of(new SaleItemRequest(1L, 3)));
        SaleResponse response = saleService.checkout(req, "cashier");

        assertThat(coffee.getStockQuantity()).isEqualTo(7);
        assertThat(response.subtotal()).isEqualByComparingTo("300.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("24.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("324.00");
        assertThat(response.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(response.receiptNumber()).startsWith("RCP-");
    }

    @Test
    void checkoutThrowsWhenStockInsufficientAndDoesNotSaveSale() {
        Product tea = new Product("SKU-2", "222", "Tea", "Drinks", new BigDecimal("50.00"), 2, 5);
        ReflectionTestUtils.setField(tea, "id", 2L);

        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(tea));

        CheckoutRequest req = new CheckoutRequest(PaymentMethod.CASH, List.of(new SaleItemRequest(2L, 5)));

        assertThatThrownBy(() -> saleService.checkout(req, "cashier"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
        assertThat(tea.getStockQuantity()).isEqualTo(2);
        verify(saleRepository, never()).save(any());
    }

    @Test
    void voidSaleRestoresInventoryAtomically() {
        Product snack = new Product("SKU-3", "333", "Chocolate", "Snacks", new BigDecimal("200.00"), 5, 2);
        ReflectionTestUtils.setField(snack, "id", 3L);

        Sale sale = new Sale("RCP-TEST-01", "cashier", PaymentMethod.QR,
                new BigDecimal("400.00"), new BigDecimal("32.00"), new BigDecimal("432.00"));
        ReflectionTestUtils.setField(sale, "id", 99L);
        sale.addItem(new SaleItem(snack, 2, new BigDecimal("400.00")));

        when(saleRepository.findByIdWithItems(99L)).thenReturn(Optional.of(sale));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(snack));
        when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));

        SaleResponse voided = saleService.voidSale(99L);

        assertThat(voided.status()).isEqualTo(SaleStatus.VOIDED);
        assertThat(snack.getStockQuantity()).isEqualTo(7);
    }
}
