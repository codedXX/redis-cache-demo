# JPA 到 MyBatis 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除 JPA，使用 MyBatis Mapper + XML 查询 `products` 表，并保持缓存与 HTTP 行为不变。

**Architecture:** `Product` 为普通 POJO；`ProductMapper` 使用 `@Mapper` 暴露 `Product selectById(Long id)`；`ProductMapper.xml` 通过显式 `resultMap` 完成列到属性的映射；Service 继续负责缓存与异常转换。

**Tech Stack:** Spring Boot 3.4.1, Java 17, MyBatis Spring Boot Starter 3.0.4, MySQL, H2, Spring Cache, JUnit 5, Mockito, MockMvc。

## Global Constraints

- 项目中不得保留 JPA/Hibernate 依赖、注解、配置或说明。
- MyBatis SQL 必须位于 `src/main/resources/mapper/ProductMapper.xml`。
- 对外接口 `GET /api/products/{id}`、缓存名 `products` 和 404 行为保持不变。
- 每个行为变更先写测试并确认测试因目标能力缺失而失败，再实现最小代码。
- 最终验证必须使用 `mvn -s .mvn/settings.xml clean test` 和 `mvn -s .mvn/settings.xml clean package`。

### Task 1: 添加 MyBatis 依赖、Mapper 契约和映射失败测试

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/example/rediscache/ProductMapper.java`
- Create: `src/main/resources/mapper/ProductMapper.xml`
- Create: `src/test/resources/application-mybatis-test.yml`
- Create: `src/test/resources/schema.sql`
- Create: `src/test/java/com/example/rediscache/ProductMapperTest.java`

**Interfaces:**
- Produces `ProductMapper.selectById(Long id): Product`。

- [ ] **Step 1: Write the failing integration test**

  使用 `@MybatisTest`、H2、`@ActiveProfiles("mybatis-test")` 和 `@ImportAutoConfiguration` 所需的默认配置，插入 `products` 测试数据，调用 `ProductMapper.selectById(1L)`，断言 id/name/price。测试直接依赖将要创建的 Mapper 接口。

- [ ] **Step 2: Run the test to verify it fails**

  Run: `mvn -s .mvn/settings.xml -Dtest=ProductMapperTest test`

  Expected: 编译或 Spring 上下文失败，原因是 `ProductMapper` 或 MyBatis 映射尚不存在。

- [ ] **Step 3: Add MyBatis dependency and minimal mapping**

  在 `pom.xml` 删除 `spring-boot-starter-data-jpa`，增加 `mybatis-spring-boot-starter:3.0.4`。新增：

  ```java
  @Mapper
  public interface ProductMapper {
      Product selectById(Long id);
  }
  ```

  XML 使用 `resultMap` 和 `SELECT id, name, price FROM products WHERE id = #{id}`。

- [ ] **Step 4: Add H2 test schema and verify the mapping test passes**

  在测试资源中创建 H2 兼容的 `products` 表和一条商品数据，配置 `spring.sql.init.mode=always` 与 H2 datasource。Run: `mvn -s .mvn/settings.xml -Dtest=ProductMapperTest test`。Expected: 映射测试 PASS。

- [ ] **Step 5: Commit**

  当前目录不是 Git 仓库，不能提交；保留文件变更并记录该限制。

### Task 2: 移除 JPA 模型与配置，迁移 Service 数据访问

**Files:**
- Modify: `src/main/java/com/example/rediscache/Product.java`
- Modify: `src/main/java/com/example/rediscache/ProductService.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/example/rediscache/RedisCacheDemoApplication.java`（仅在需要补充 Mapper 扫描时修改）
- Test: `src/test/java/com/example/rediscache/ProductServiceCacheTest.java`

**Interfaces:**
- Consumes `ProductMapper.selectById(Long id): Product`。
- Preserves `ProductService.getProductById(Long id): Product`。

- [ ] **Step 1: Change the cache test to use the Mapper contract and make it fail**

  将测试中的 `ProductRepository` 替换为 `ProductMapper`，用 `when(productMapper.selectById(1L)).thenReturn(databaseProduct)`，并把验证改为 `verify(productMapper, times(1)).selectById(1L)`。Run the focused test; expected failure until Service injection is migrated。

- [ ] **Step 2: Implement the minimal migration**

  删除 `Product` 的 `jakarta.persistence` import 和注解，保留构造器、getter/setter。Service 注入 `ProductMapper`，调用 `selectById`，并在结果为 `null` 时抛出 `ProductNotFoundException`。删除 `application.yml` 的 `spring.jpa` 段。

- [ ] **Step 3: Verify the cache test**

  Run: `mvn -s .mvn/settings.xml -Dtest=ProductServiceCacheTest test`。Expected: PASS，且 Mapper 只调用一次。

### Task 3: 更新 Controller 集成测试和项目文档

**Files:**
- Modify: `src/test/java/com/example/rediscache/ProductControllerTest.java`
- Modify: `src/main/resources/database/schema.sql`
- Modify: `README.md`
- Modify: `Redis缓存学习笔记.md`

- [ ] **Step 1: Update Controller test configuration and dependency type**

  将 `@MockitoBean ProductRepository` 改为 `ProductMapper`，将 stub/verify 改为 `selectById`，删除 `spring.jpa.hibernate.ddl-auto` 属性；测试继续使用 `spring.cache.type=simple`，不连接真实 MySQL/Redis。

- [ ] **Step 2: Run Controller tests and fix only migration-related failures**

  Run: `mvn -s .mvn/settings.xml -Dtest=ProductControllerTest test`。Expected: HTTP 200、JSON 字段、缓存只查询一次的断言全部 PASS。

- [ ] **Step 3: Remove stale JPA wording**

  将 SQL、README 和学习笔记中描述 JPA Repository/实体的内容改成 MyBatis Mapper/XML；不改变文档中的 Redis 教学目标和命令。

- [ ] **Step 4: Verify no JPA references remain**

  Run: `rg -n -i "jpa|hibernate|jakarta.persistence|JpaRepository|spring\.jpa" pom.xml src README.md Redis缓存学习笔记.md`。

  Expected: 无输出。

### Task 4: 全量验证

**Files:**
- Inspect all changed files and generated build output。

- [ ] **Step 1: Run clean tests**

  Run: `mvn -s .mvn/settings.xml clean test`。

  Expected: BUILD SUCCESS，所有测试通过。

- [ ] **Step 2: Run clean package**

  Run: `mvn -s .mvn/settings.xml clean package`。

  Expected: BUILD SUCCESS，生成 `target/redis-cache-demo-1.0.0.jar`。

- [ ] **Step 3: Inspect dependency tree and source references**

  Run: `mvn -s .mvn/settings.xml dependency:tree` and `rg -n -i "jpa|hibernate|jakarta.persistence|JpaRepository|spring\.jpa" pom.xml src README.md Redis缓存学习笔记.md`。

  Expected: 依赖树不含 JPA/Hibernate，源码和文档检索无输出。
