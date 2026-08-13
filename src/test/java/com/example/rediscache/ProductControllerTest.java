package com.example.rediscache;

import com.example.rediscache.mapper.ProductMapper;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.example.rediscache.pojo.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 验证 HTTP JSON 响应，并通过两次请求验证缓存流程。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cache_demo;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.cache.type=simple"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ProductMapper productMapper;

    @Test
    void firstHttpRequestReadsMapperAndSecondHttpRequestReadsCache() throws Exception {
        cacheManager.getCache("products").clear();
        when(productMapper.selectById(1L))
                .thenReturn(new Product(1L, "Java 编程思想", new BigDecimal("88.00")));

        mockMvc.perform(get("/api/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Java 编程思想"))
                .andExpect(jsonPath("$.price").value(88.00));

        mockMvc.perform(get("/api/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(productMapper, times(1)).selectById(1L);
    }
}
