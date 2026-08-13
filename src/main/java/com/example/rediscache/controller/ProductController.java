package com.example.rediscache.controller;

import com.example.rediscache.pojo.Product;
import com.example.rediscache.exception.ProductNotFoundException;
import com.example.rediscache.service.ProductService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品 HTTP 接口，供 curl 或 Postman 触发缓存流程。 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 访问同一商品两次即可分别观察缓存未命中和命中。 */
    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id){
    return productService.getProductById(id);
    }

    /** 将商品不存在的业务异常转换为清晰的 404 JSON 响应。 */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", exception.getMessage()));
    }
}
