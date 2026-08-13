# Redis 分布式布隆过滤器实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**目标：** 在现有 MyBatis + Spring Cache 商品查询链路中加入 Redis 分布式布隆过滤器，拦截确定不存在的商品 ID。

**架构：** 通过 `ProductBloomFilter` 抽象隔离 Service 与 Redisson；生产环境使用 Redis `RBloomFilter`，启动时由 MySQL 批量商品 ID 初始化；测试环境使用 Mockito 替身，不要求本机 Redis。

**技术栈：** Spring Boot 3.4.1、Java 17、MyBatis、Spring Cache、Redisson、JUnit 5、Mockito、H2。

## 约束

- 不引入 JPA/Hibernate。
- Redis 过滤器名称固定为 `products:bloom`，并支持通过配置覆盖。
- 布隆过滤器只能作为快速排除层，数据库仍是最终事实来源。
- 每个行为变更先补测试，再实现代码。
- 通过 Maven 测试和打包验证。

## Task 1：补充 Mapper 批量读取和失败测试

**文件：**

- 修改 `src/main/java/com/example/rediscache/mapper/ProductMapper.java`
- 修改 `src/main/resources/mapper/ProductMapper.xml`
- 修改 `src/test/java/com/example/rediscache/ProductMapperTest.java`
- 修改 `src/test/java/com/example/rediscache/ProductServiceCacheTest.java`

- [ ] 添加 `selectAllIds()` 契约和 XML SQL。
- [ ] 添加 Service 测试：过滤器返回 false 时抛出 404 异常且 Mapper 不被调用。
- [ ] 运行聚焦测试，确认在过滤器接入前失败或无法编译。

## Task 2：实现 Redis 布隆过滤器

**文件：**

- 修改 `pom.xml`
- 新增 `src/main/java/com/example/rediscache/config/RedissonConfig.java`
- 新增 `src/main/java/com/example/rediscache/filter/ProductBloomFilter.java`
- 新增 `src/main/java/com/example/rediscache/filter/RedisProductBloomFilter.java`
- 新增 `src/main/java/com/example/rediscache/filter/ProductBloomFilterInitializer.java`

- [ ] 添加 Redisson 依赖。
- [ ] 根据 `spring.data.redis` 创建 Redisson 客户端。
- [ ] 实现过滤器的初始化、`mightContain` 和 `add`。
- [ ] 启动时读取全部商品 ID 并写入过滤器。

## Task 3：接入 Service、配置和测试

**文件：**

- 修改 `src/main/java/com/example/rediscache/service/ProductService.java`
- 修改 `src/main/resources/application.yml`
- 修改 `src/test/java/com/example/rediscache/ProductServiceCacheTest.java`
- 修改 `src/test/java/com/example/rediscache/ProductControllerTest.java`
- 新增 `src/test/java/com/example/rediscache/RedisProductBloomFilterTest.java`
- 新增 `src/test/java/com/example/rediscache/ProductBloomFilterInitializerTest.java`

- [ ] 在缓存方法的数据库回源逻辑前执行布隆过滤器判断。
- [ ] 更新测试替身，使正常商品继续走缓存和 Mapper。
- [ ] 验证 Redisson API 委托和启动批量初始化行为。
- [ ] 在 `application.yml` 增加过滤器容量、误判率和名称配置。

## Task 4：文档和全量验证

**文件：**

- 修改 `README.md`
- 修改 `Redis缓存学习笔记.md`

- [ ] 补充过滤器的工作流程、误判特性和新增商品写入要求。
- [ ] 运行指定测试和全量测试。
- [ ] 运行打包命令，确认构建结果。
- [ ] 检查源码中无残留 JPA 引用，并汇报无法连接真实 Redis 时的验证边界。
