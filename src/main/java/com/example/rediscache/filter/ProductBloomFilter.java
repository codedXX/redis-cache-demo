package com.example.rediscache.filter;

import java.util.Collection;

/** 商品 ID 布隆过滤器抽象，避免业务层直接依赖 Redisson API。 */
public interface ProductBloomFilter {

    boolean mightContain(Long productId);

    void add(Long productId);

    void initialize(Collection<Long> productIds);
}
