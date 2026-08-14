package com.example.rediscache;

import com.example.rediscache.exception.ProductNotFoundException;
import com.example.rediscache.filter.ProductBloomFilter;
import com.example.rediscache.mapper.ProductMapper;
import com.example.rediscache.pojo.Product;
import com.example.rediscache.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductServiceCacheTest {

    private ProductMapper productMapper;
    private ProductService productService;
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() throws Exception {
        productMapper = mock(ProductMapper.class);
        TestCacheConfig.mapper = productMapper;
        TestCacheConfig.bloomFilter = mock(ProductBloomFilter.class);
        TestCacheConfig.redissonClient = mock(RedissonClient.class);
        TestCacheConfig.lock = mock(RLock.class);
        when(TestCacheConfig.redissonClient.getLock(anyString())).thenReturn(TestCacheConfig.lock);
        doReturn(true).when(TestCacheConfig.lock).tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        when(TestCacheConfig.lock.isHeldByCurrentThread()).thenReturn(true);
        context = new AnnotationConfigApplicationContext(TestCacheConfig.class);
        productService = context.getBean(ProductService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void firstRequestReadsDatabaseAndSecondRequestReadsCache() {
        Product databaseProduct = new Product(1L, "Java", new BigDecimal("88.00"));
        when(TestCacheConfig.bloomFilter.mightContain(1L)).thenReturn(true);
        when(productMapper.selectById(1L)).thenReturn(databaseProduct);

        Product firstResult = productService.getProductById(1L);
        Product secondResult = productService.getProductById(1L);

        assertThat(firstResult).isEqualTo(databaseProduct);
        assertThat(secondResult).isEqualTo(databaseProduct);
        verify(productMapper, times(1)).selectById(1L);
    }

    @Test
    void definitelyMissingProductIsRejectedBeforeDatabaseQuery() {
        when(TestCacheConfig.bloomFilter.mightContain(999L)).thenReturn(false);

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(productMapper);
    }

    @Test
    void cacheMissLoadsDatabaseOnceAndReleasesLock() throws Exception {
        Product databaseProduct = new Product(2L, "Redis", new BigDecimal("99.00"));
        when(TestCacheConfig.bloomFilter.mightContain(2L)).thenReturn(true);
        when(productMapper.selectById(2L)).thenReturn(databaseProduct);

        Product result = productService.getProductById(2L);

        assertThat(result).isEqualTo(databaseProduct);
        assertThat(context.getBean(CacheManager.class).getCache("products").get(2L).get())
                .isEqualTo(databaseProduct);
        verify(productMapper, times(1)).selectById(2L);
        verify(TestCacheConfig.lock).tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(TestCacheConfig.lock).unlock();
    }

    @Test
    void waitingRequestReadsCacheAfterAnotherRequestRebuildsIt() throws Exception {
        Product rebuiltProduct = new Product(3L, "Lock", new BigDecimal("1.00"));
        doAnswer(invocation -> {
            context.getBean(CacheManager.class).getCache("products").put(3L, rebuiltProduct);
            return false;
        }).when(TestCacheConfig.lock).tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));

        Product result = productService.getProductById(3L);

        assertThat(result).isEqualTo(rebuiltProduct);
        verifyNoInteractions(productMapper);
        verify(TestCacheConfig.lock, times(0)).unlock();
    }

    @Test
    void lockHolderPerformsSecondCacheCheckBeforeDatabaseQuery() throws Exception {
        Product alreadyRebuilt = new Product(4L, "DoubleCheck", new BigDecimal("2.00"));
        doAnswer(invocation -> {
            context.getBean(CacheManager.class).getCache("products").put(4L, alreadyRebuilt);
            return true;
        }).when(TestCacheConfig.lock).tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));

        Product result = productService.getProductById(4L);

        assertThat(result).isEqualTo(alreadyRebuilt);
        verifyNoInteractions(productMapper);
        verify(TestCacheConfig.lock).unlock();
    }

    @Configuration
    @EnableCaching
    static class TestCacheConfig {
        private static ProductMapper mapper;
        private static ProductBloomFilter bloomFilter;
        private static RedissonClient redissonClient;
        private static RLock lock;

        @Bean
        ProductMapper productMapper() {
            return mapper;
        }

        @Bean
        ProductBloomFilter productBloomFilter() {
            return bloomFilter;
        }

        @Bean
        RedissonClient redissonClient() {
            return redissonClient;
        }

        @Bean
        ProductService productService(ProductMapper mapper, ProductBloomFilter productBloomFilter,
                                      CacheManager cacheManager, RedissonClient redissonClient) {
            return new ProductService(mapper, productBloomFilter, cacheManager, redissonClient,
                    3000L, 10000L, true);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("products");
        }
    }
}
