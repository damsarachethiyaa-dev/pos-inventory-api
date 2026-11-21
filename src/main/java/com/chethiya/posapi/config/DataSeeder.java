package com.chethiya.posapi.config;

import com.chethiya.posapi.domain.Product;
import com.chethiya.posapi.domain.Role;
import com.chethiya.posapi.domain.User;
import com.chethiya.posapi.repository.ProductRepository;
import com.chethiya.posapi.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            UserRepository userRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(new User(
                    "admin",
                    passwordEncoder.encode("Admin@1234"),
                    "Store Administrator",
                    Role.ADMIN));
        }
        if (!userRepository.existsByUsername("cashier")) {
            userRepository.save(new User(
                    "cashier",
                    passwordEncoder.encode("Cashier@1234"),
                    "Front Desk Cashier",
                    Role.CASHIER));
        }
        if (productRepository.count() == 0) {
            productRepository.saveAll(List.of(
                    new Product("SKU-COF-001", "8901234000011", "Ceylon Arabica Coffee 250g", "Beverages",
                            new BigDecimal("1450.00"), 40, 10),
                    new Product("SKU-TEA-002", "8901234000028", "Nuwara Eliya Pure Black Tea 200g", "Beverages",
                            new BigDecimal("980.00"), 25, 8),
                    new Product("SKU-SNK-003", "8901234000035", "Dark Chocolate Bar 70%", "Snacks",
                            new BigDecimal("650.00"), 6, 10)
            ));
        }
    }
}
