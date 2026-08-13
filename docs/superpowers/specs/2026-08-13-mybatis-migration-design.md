# JPA 到 MyBatis 迁移设计

## 目标

移除项目对 JPA/Hibernate 的依赖，使用 MyBatis Mapper 接口与 XML SQL 映射访问 `products` 表，同时保持现有商品查询、Redis 缓存和 HTTP 接口行为不变。

## 架构

`Product` 保留为无框架依赖的普通 Java Bean。`ProductMapper` 作为数据访问边界，暴露 `Product selectById(Long id)`，由 `ProductMapper.xml` 映射 `products` 表的列到 `Product` 属性。`ProductService` 继续负责缓存和未找到异常转换，Mapper 返回 `null` 时由 Service 转换为 `ProductNotFoundException`。

## 配置与依赖

- 删除 `spring-boot-starter-data-jpa`。
- 增加 `org.mybatis.spring.boot:mybatis-spring-boot-starter`，版本使用与 Spring Boot 3.4.1 兼容的 3.0.4。
- 删除 `spring.jpa` 配置。
- 增加 `mybatis.mapper-locations: classpath:mapper/*.xml`，并使用 `@Mapper` 扫描 Mapper 接口。
- 保留 MySQL 驱动、H2 测试依赖以及现有 Redis 配置。

## 数据访问

Mapper XML 使用显式 `resultMap`，将 `id`、`name`、`price` 分别映射到 `Product.id`、`Product.name`、`Product.price`。查询 SQL 为参数化 SQL：

```sql
SELECT id, name, price FROM products WHERE id = #{id}
```

生产代码不保留任何 `jakarta.persistence`、Spring Data JPA 或 Hibernate 类型引用。

## 测试

- 增加 MyBatis 映射集成测试，使用 H2 建表和插入数据，验证 `ProductMapper.selectById` 的三列映射。
- 更新缓存单元测试和 Controller 测试中的数据访问类型、返回值断言及测试配置。
- 继续验证首次请求查询数据库、第二次请求命中缓存，以及不存在商品返回 404。
- 以 `mvn -s .mvn/settings.xml clean test` 和 `mvn -s .mvn/settings.xml clean package` 作为最终验证命令。

## 文档

更新 README、SQL 注释和 Java 注释，统一描述为 MyBatis 数据访问，不保留误导性的 JPA/Hibernate 说明。
