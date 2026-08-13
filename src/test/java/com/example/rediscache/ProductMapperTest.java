package com.example.rediscache;

import com.example.rediscache.mapper.ProductMapper;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.example.rediscache.pojo.Product;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

@MybatisTest
@ActiveProfiles("mybatis-test")
@Sql(scripts = "/schema.sql", config = @SqlConfig(encoding = "UTF-8"))
class ProductMapperTest {

    @Autowired
    private ProductMapper productMapper;

    @Test
    void selectByIdMapsProductColumns() {
        Product product = productMapper.selectById(1L);

        assertThat(product).isNotNull();
        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("Java 编程思想");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("88.00"));
    }
}
