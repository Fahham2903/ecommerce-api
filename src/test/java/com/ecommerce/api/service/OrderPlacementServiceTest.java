package com.ecommerce.api.service;

import com.ecommerce.api.dto.CheckoutItem;
import com.ecommerce.api.dto.OrderResponse;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.Role;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.exception.InsufficientStockException;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @InjectMocks OrderPlacementService service;

    private User user() {
        User u = new User();
        u.setEmail("buyer@example.com");
        u.setRole(Role.CUSTOMER);
        return u;
    }

    private Product product(long id, int stock, String price) {
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", id);
        p.setName("Item " + id);
        p.setCategory("misc");
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        return p;
    }

    @Test
    void rejectsCheckoutWhenStockIsInsufficient() {
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user()));
        Product p = product(1L, 2, "10.00");
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.place("buyer@example.com", List.of(new CheckoutItem(1L, 3))))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(p.getStock()).isEqualTo(2);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void deductsStockAndComputesTotalIncludingMergedDuplicateLines() {
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user()));
        Product p1 = product(1L, 10, "10.00");
        Product p2 = product(2L, 5, "2.50");
        when(productRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(orderRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.place("buyer@example.com",
                List.of(new CheckoutItem(1L, 2), new CheckoutItem(1L, 1), new CheckoutItem(2L, 4)));

        assertThat(p1.getStock()).isEqualTo(7);
        assertThat(p2.getStock()).isEqualTo(1);
        assertThat(response.total()).isEqualByComparingTo("40.00");   // 3 x 10.00 + 4 x 2.50
        assertThat(response.items()).hasSize(2);
    }
}
