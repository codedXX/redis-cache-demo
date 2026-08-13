package com.example.rediscache.service;

import com.example.rediscache.pojo.Product;
import com.example.rediscache.exception.ProductNotFoundException;
import com.example.rediscache.mapper.ProductMapper;
import com.example.rediscache.filter.ProductBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 商品业务服务，缓存边界集中在这里，便于观察命中与未命中。 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductMapper productMapper;
    private final ProductBloomFilter productBloomFilter;

    public ProductService(ProductMapper productMapper, ProductBloomFilter productBloomFilter) {
        this.productMapper = productMapper;
        this.productBloomFilter = productBloomFilter;
    }

    /** 查询商品详情，首次请求查询 MySQL，后续相同 ID 的请求从缓存返回。 */
    @Cacheable(cacheNames = "products", key = "#id")
    public Product getProductById(Long id) {
        if (!productBloomFilter.mightContain(id)) {
            throw new ProductNotFoundException(id);
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("商品查询被中断", e);
        }
        log.info("CACHE_MISS: Redis 未命中，开始查询 MySQL，productId={}", id);
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }
}
