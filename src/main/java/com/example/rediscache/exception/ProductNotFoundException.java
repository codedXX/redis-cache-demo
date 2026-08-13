package com.example.rediscache.exception;

/** 商品不存在时抛出的业务异常。 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("商品不存在，id=" + id);
    }
}
