package com.ecommerce.api.service;

import com.ecommerce.api.dto.ProductRequest;
import com.ecommerce.api.dto.ProductResponse;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.exception.NotFoundException;
import com.ecommerce.api.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) { this.productRepository = productRepository; }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        Product p = new Product();
        apply(p, r);
        return ProductResponse.from(productRepository.save(p));
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse update(Long id, ProductRequest r) {
        Product p = productRepository.findById(id).orElseThrow(() -> notFound(id));
        apply(p, r);
        return ProductResponse.from(p);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void delete(Long id) {
        if (!productRepository.existsById(id)) throw notFound(id);
        productRepository.deleteById(id);
    }

    /** Hot path: single-product lookups are cached in Redis (TTL 10m, evicted on update/delete/checkout). */
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productRepository.findById(id).map(ProductResponse::from).orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String q, String category, Pageable pageable) {
        Page<Product> page = (category == null || category.isBlank())
                ? productRepository.findByNameContainingIgnoreCase(q, pageable)
                : productRepository.findByNameContainingIgnoreCaseAndCategoryIgnoreCase(q, category, pageable);
        return page.map(ProductResponse::from);
    }

    private void apply(Product p, ProductRequest r) {
        p.setName(r.name());
        p.setDescription(r.description());
        p.setCategory(r.category());
        p.setPrice(r.price());
        p.setStock(r.stock());
    }

    private NotFoundException notFound(Long id) { return new NotFoundException("Product " + id + " not found"); }
}
