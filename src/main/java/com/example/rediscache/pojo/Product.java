package com.example.rediscache.pojo;

import java.math.BigDecimal;

/** 商品数据对象，对应数据库中的 products 表。 */
public class Product {

    private Long id;
    private String name;
    private BigDecimal price;

    protected Product() {
        // MyBatis 映射查询结果时需要无参构造方法。
    }

    public Product(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
