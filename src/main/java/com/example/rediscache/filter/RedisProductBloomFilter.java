package com.example.rediscache.filter;

import java.util.Collection;
import java.util.Objects;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 基于 Redis 的分布式商品 ID 布隆过滤器。 */
@Component
@Profile("!test")
public class RedisProductBloomFilter implements ProductBloomFilter {

    private final RBloomFilter<Long> bloomFilter;
    private final long expectedInsertions;
    private final double falsePositiveProbability;

    public RedisProductBloomFilter(
            RedissonClient redissonClient,
            @Value("${app.bloom-filter.name:products:bloom}") String filterName,
            @Value("${app.bloom-filter.expected-insertions:100000}") long expectedInsertions,
            @Value("${app.bloom-filter.false-positive-probability:0.01}") double falsePositiveProbability) {
        this.bloomFilter = redissonClient.getBloomFilter(filterName);
        this.expectedInsertions = expectedInsertions;
        this.falsePositiveProbability = falsePositiveProbability;
    }

    @Override
    public boolean mightContain(Long productId) {
        return productId != null && bloomFilter.contains(productId);
    }

    @Override
    public void add(Long productId) {
        if (productId != null) {
            bloomFilter.add(productId);
        }
    }

    @Override
    public void initialize(Collection<Long> productIds) {
        bloomFilter.tryInit(expectedInsertions, falsePositiveProbability);
        if (productIds != null) {
            productIds.stream()
                    .filter(Objects::nonNull)
                    .forEach(bloomFilter::add);
        }
    }
}
