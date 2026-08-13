-- 1. 创建教学项目专用数据库，避免与已有业务库混用。
CREATE DATABASE IF NOT EXISTS redis_cache_demo
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE redis_cache_demo;

-- 2. 商品表：MyBatis 的 ProductMapper 查询该表。
CREATE TABLE IF NOT EXISTS products (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品主键',
  name VARCHAR(100) NOT NULL COMMENT '商品名称',
  price DECIMAL(10, 2) NOT NULL COMMENT '商品价格',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Redis 缓存演示商品表';

-- 3. 示例数据。重复执行脚本时先清空，保证商品 ID 固定为 1 和 2。
DELETE FROM products;
ALTER TABLE products AUTO_INCREMENT = 1;

INSERT INTO products (name, price) VALUES
  ('Java 编程思想', 88.00),
  ('Spring Boot 实战', 99.00);
