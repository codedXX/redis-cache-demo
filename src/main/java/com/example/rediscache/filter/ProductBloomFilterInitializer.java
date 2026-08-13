package com.example.rediscache.filter;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.rediscache.mapper.ProductMapper;

/** 应用启动时把数据库中的商品 ID 同步到 Redis 布隆过滤器。 */
@Component
@Profile("!test")
public class ProductBloomFilterInitializer {

    private final ProductMapper productMapper;
    private final ProductBloomFilter productBloomFilter;

    public ProductBloomFilterInitializer(ProductMapper productMapper, ProductBloomFilter productBloomFilter) {
        this.productMapper = productMapper;
        this.productBloomFilter = productBloomFilter;
    }

    @PostConstruct
    public void initialize() {
        productBloomFilter.initialize(productMapper.selectAllIds());
    }
}
