package com.example.rediscache;

import com.example.rediscache.mapper.ProductMapper;
import com.example.rediscache.pojo.Product;
import com.example.rediscache.service.ProductService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 验证第一次查询 Mapper、第二次请求读取缓存。 */
class ProductServiceCacheTest {

    private ProductMapper productMapper;
    private ProductService productService;
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        TestCacheConfig.mapper = productMapper;

        context = new AnnotationConfigApplicationContext(TestCacheConfig.class);
        productService = context.getBean(ProductService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void firstRequestReadsDatabaseAndSecondRequestReadsCache() {
        Product databaseProduct = new Product(1L, "Java 编程思想", new BigDecimal("88.00"));
        when(productMapper.selectById(1L)).thenReturn(databaseProduct);

        Product firstResult = productService.getProductById(1L);
        Product secondResult = productService.getProductById(1L);

        assertThat(firstResult).isEqualTo(databaseProduct);
        assertThat(secondResult).isEqualTo(databaseProduct);
        verify(productMapper, times(1)).selectById(1L);
    }

    @Configuration
    @EnableCaching
    static class TestCacheConfig {
        private static ProductMapper mapper;

        @Bean
        ProductMapper productMapper() {
            return mapper;
        }

        @Bean
        ProductService productService(ProductMapper mapper) {
            return new ProductService(mapper);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("products");
        }
    }
}
