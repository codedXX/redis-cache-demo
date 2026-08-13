package com.example.rediscache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.example.rediscache.filter.RedisProductBloomFilter;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

class RedisProductBloomFilterTest {

    @Test
    void delegatesInitializationAndWritesToRedissonBloomFilter() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<Long> bloomFilter = mock(RBloomFilter.class);
        when(redissonClient.<Long>getBloomFilter("products:bloom")).thenReturn(bloomFilter);

        RedisProductBloomFilter productBloomFilter = new RedisProductBloomFilter(
                redissonClient, "products:bloom", 100_000L, 0.01D);

        productBloomFilter.initialize(List.of(1L, 2L));
        productBloomFilter.add(3L);

        verify(bloomFilter).tryInit(100_000L, 0.01D);
        verify(bloomFilter).add(1L);
        verify(bloomFilter).add(2L);
        verify(bloomFilter).add(3L);
    }

    @Test
    void returnsFalseForNullAndDelegatesContainsForAnId() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<Long> bloomFilter = mock(RBloomFilter.class);
        when(redissonClient.<Long>getBloomFilter("products:bloom")).thenReturn(bloomFilter);
        when(bloomFilter.contains(1L)).thenReturn(true);

        RedisProductBloomFilter productBloomFilter = new RedisProductBloomFilter(
                redissonClient, "products:bloom", 100_000L, 0.01D);

        assertThat(productBloomFilter.mightContain(null)).isFalse();
        assertThat(productBloomFilter.mightContain(1L)).isTrue();
        verify(bloomFilter).contains(1L);
    }
}
