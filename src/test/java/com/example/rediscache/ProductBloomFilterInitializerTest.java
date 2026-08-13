package com.example.rediscache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.example.rediscache.filter.ProductBloomFilter;
import com.example.rediscache.filter.ProductBloomFilterInitializer;
import com.example.rediscache.mapper.ProductMapper;
import org.junit.jupiter.api.Test;

class ProductBloomFilterInitializerTest {

    @Test
    void loadsAllProductIdsFromMapperAtStartup() {
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductBloomFilter productBloomFilter = mock(ProductBloomFilter.class);
        when(productMapper.selectAllIds()).thenReturn(List.of(1L, 2L));

        ProductBloomFilterInitializer initializer =
                new ProductBloomFilterInitializer(productMapper, productBloomFilter);

        initializer.initialize();

        verify(productBloomFilter).initialize(List.of(1L, 2L));
    }
}
