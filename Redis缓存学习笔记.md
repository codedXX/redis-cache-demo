# Spring Boot Redis 缓存学习笔记

本文以当前项目的商品查询接口为例，介绍 Spring Cache 与 Redis 的分工、常用缓存注解，以及查询、更新、删除时的完整缓存流程。

## 一、整体关系

Spring Cache 和 Redis 不是同一个东西：

~~~text
业务代码
   |
   | 使用 @Cacheable、@CachePut、@CacheEvict
   v
Spring Cache（缓存抽象层）
   |
   | 通过 CacheManager 执行读写
   v
RedisCacheManager
   |
   v
Redis（实际保存缓存数据）
~~~

Spring Cache 定义统一的缓存编程模型，Redis 负责真正保存数据。业务代码只需要声明哪些方法需要缓存，不必在每个方法里手写 GET、SET、序列化和过期时间逻辑。

## 二、两个 starter 的作用

### 1. spring-boot-starter-cache

依赖：

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
~~~

它提供 Spring Cache 的缓存抽象能力，主要包括：

- @Cacheable：查询缓存，缓存未命中时执行方法并保存结果。
- @CachePut：每次都执行方法，并把返回值更新到缓存。
- @CacheEvict：删除一个或多个缓存项。
- @Caching：在一个方法上组合多个缓存操作。
- @CacheConfig：在类级别统一设置缓存名称等默认配置。
- @EnableCaching：开启注解驱动的缓存拦截器。
- CacheManager：缓存管理器的统一接口。

注意：这个 starter 本身不等于 Redis。它只定义缓存规则，具体数据存在哪里取决于项目引入的缓存实现。

### 2. spring-boot-starter-data-redis

依赖：

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
~~~

它提供 Spring Data Redis 和 Redis 客户端能力，当前项目默认使用 Lettuce，主要负责：

- 创建 Redis 连接。
- 读写 Redis 的字符串、哈希、列表、集合等数据结构。
- 提供 RedisTemplate 和 StringRedisTemplate。
- 提供 Redis 版的 RedisCacheManager。
- 将 Spring Cache 的缓存操作转换成 Redis 命令。

两个依赖的关系：

| 依赖 | 主要职责 | 典型内容 |
| --- | --- | --- |
| spring-boot-starter-cache | 缓存抽象与注解 | @Cacheable、@CachePut、@CacheEvict |
| spring-boot-starter-data-redis | Redis 连接与数据存储 | RedisTemplate、Lettuce、RedisCacheManager |

在本项目中，两者配合后，@Cacheable 产生的缓存最终存储在 Redis 中。

## 三、开启缓存功能

在启动类上增加 @EnableCaching：

~~~java
@SpringBootApplication
@EnableCaching
public class RedisCacheDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisCacheDemoApplication.class, args);
    }
}
~~~

@EnableCaching 会让 Spring 创建缓存代理。当外部调用带有缓存注解的 Bean 方法时，代理会先检查缓存，再决定是否真正执行方法体。

如果忘记添加它，注解仍然可以编译，但缓存拦截器不会生效，方法每次都会执行。

## 四、本项目的 Redis 配置

当前项目 application.yml 的核心配置：

~~~yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DATABASE:0}
  cache:
    type: redis
~~~

含义：

- Redis 地址是 localhost:6379。
- Redis 逻辑数据库是 db0。
- 使用 Redis 作为 Spring Cache 的实现。
- 可以通过环境变量覆盖默认值。

项目还在 CacheConfig 中自定义了 Redis 缓存管理器：

~~~java
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

    return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(configuration)
            .build();
}
~~~

当前项目的缓存特点：

- 默认过期时间为 10 分钟。
- 值使用 JSON 序列化，便于查看。
- 不缓存 null。
- 商品 ID 为 1 时，默认键名是 products::1。
- 数据存储在 Redis 的 db0。

查看缓存：

~~~bash
redis-cli -n 0 SCAN 0 MATCH 'products::*'
redis-cli -n 0 TTL products::1
redis-cli -n 0 GET products::1
~~~

生产环境不建议频繁使用 KEYS，应使用更安全的 SCAN。

## 五、@Cacheable：查询缓存

### 1. 基本作用

@Cacheable 用于查询多、修改少的方法。调用方法时，Spring 会先根据缓存名称和 key 查询缓存：

~~~text
缓存命中 -> 直接返回缓存值，不执行方法体
缓存未命中 -> 执行方法体，把返回值写入缓存，再返回结果
~~~

当前项目的代码：

~~~java
@Cacheable(cacheNames = "products", key = "#id")
public Product getProductById(Long id) {
    log.info("CACHE_MISS: Redis 未命中，开始查询 MySQL，productId={}", id);
    Product product = productMapper.selectById(id);
    if (product == null) {
        throw new ProductNotFoundException(id);
    }
    return product;
}
~~~

第一次请求 GET /api/products/1：

~~~text
Redis 中没有 products::1
    -> 执行 getProductById 方法体
    -> 查询 MySQL
    -> 返回 Product
    -> 写入 Redis products::1
~~~

第二次请求相同 ID：

~~~text
Redis 中存在 products::1
    -> 直接返回 Redis 中的 Product
    -> 不执行方法体
    -> 不查询 MySQL
~~~

### 2. 常用属性

#### cacheNames 或 value

指定缓存名称，两者是同义属性：

~~~java
@Cacheable(cacheNames = "products")
public Product find(Long id) { ... }

@Cacheable(value = "products")
public Product find(Long id) { ... }
~~~

缓存名称通常对应一个缓存区域，不是 Redis 的逻辑数据库。当前项目的 products 是缓存区域，Redis key 前缀是 products::。

#### key

使用 SpEL 指定 key：

~~~java
@Cacheable(cacheNames = "products", key = "#id")
@Cacheable(cacheNames = "products", key = "#product.id")
@Cacheable(cacheNames = "products", key = "'detail:' + #id")
~~~

如果不指定 key，Spring 会根据方法参数生成 key。建议在业务代码中明确写出 key 规则。

#### condition

在方法执行前判断是否允许使用缓存：

~~~java
@Cacheable(
        cacheNames = "products",
        key = "#id",
        condition = "#id > 0"
)
public Product getProductById(Long id) { ... }
~~~

当 id 小于等于 0 时，不读缓存，也不写缓存，但方法仍然执行。

#### unless

在方法执行后判断是否禁止写入缓存：

~~~java
@Cacheable(
        cacheNames = "products",
        key = "#id",
        unless = "#result == null"
)
public Product getProductById(Long id) { ... }
~~~

unless 可以访问返回值 #result。方法仍会执行，但符合条件的结果不会写入缓存。

#### sync

在同一个应用实例内，对同一个 key 的并发未命中进行同步：

~~~java
@Cacheable(cacheNames = "products", key = "#id", sync = true)
public Product getProductById(Long id) { ... }
~~~

它可以减少同一实例内的缓存击穿，但不是跨多个应用实例的分布式锁。

## 六、@CachePut：执行方法并更新缓存

@CachePut 与 @Cacheable 的区别：

- @Cacheable：命中时跳过方法。
- @CachePut：每次都执行方法，然后用返回值更新缓存。

适合“更新数据库，再把最新对象放进缓存”的场景：

~~~java
@CachePut(cacheNames = "products", key = "#product.id")
public Product updateProduct(Product product) {
    // 通过 ProductMapper 中对应的 update SQL 持久化商品
    Product saved = product;
    return saved;
}
~~~

执行流程：

~~~text
调用 updateProduct
    -> 更新 MySQL
    -> 得到最新 Product
    -> 用返回值覆盖 products::id
~~~

更新方法应明确返回最终写入缓存的对象，并确保事务边界合理。

## 七、@CacheEvict：删除缓存

数据库发生变化而缓存仍保留旧值时，就会出现脏数据。@CacheEvict 用于删除缓存。

### 1. 删除指定 key

~~~java
@CacheEvict(cacheNames = "products", key = "#id")
public void deleteProduct(Long id) {
    // 通过 ProductMapper 中对应的 delete SQL 删除商品
}
~~~

成功调用后会删除 products::id。

### 2. 更新数据库后删除旧缓存

一种常见的 Cache-Aside 写法是：修改数据库，成功后删除缓存，让下一次查询重新加载：

~~~java
@CacheEvict(cacheNames = "products", key = "#id")
public Product updateProduct(Long id, ProductRequest request) {
    Product product = productMapper.selectById(id);
    if (product == null) {
        throw new ProductNotFoundException(id);
    }
    product.setName(request.name());
    product.setPrice(request.price());
    // 通过 ProductMapper 中对应的 update SQL 持久化商品
    return product;
}
~~~

之后第一次查询会重新访问 MySQL，再把新值写回 Redis。

### 3. 清空整个缓存区域

~~~java
@CacheEvict(cacheNames = "products", allEntries = true)
public void clearProductCache() {
    // products 缓存区域中的所有 key 都会被清除
}
~~~

清空大量缓存可能带来瞬时数据库压力，应谨慎使用。

### 4. 在方法执行前清除

~~~java
@CacheEvict(
        cacheNames = "products",
        key = "#id",
        beforeInvocation = true
)
public void deleteProductImmediately(Long id) {
    // 通过 ProductMapper 中对应的 delete SQL 删除商品
}
~~~

beforeInvocation = true 时，即使方法抛异常，缓存也已经被清除。是否使用取决于业务对失败回滚和缓存一致性的要求。

## 八、@Caching：组合多个缓存操作

一个方法可能需要同时清除多个缓存区域或多个 key：

~~~java
@Caching(evict = {
        @CacheEvict(cacheNames = "products", key = "#id"),
        @CacheEvict(cacheNames = "productList", allEntries = true)
})
public Product updateProductAndClearLists(Long id, ProductRequest request) {
    // 更新数据库
    return ...;
}
~~~

含义是删除商品详情缓存，并清空商品列表缓存。

如果商品同时出现在详情缓存、列表缓存、分类缓存中，更新时相关列表缓存也需要处理。

## 九、@CacheConfig：类级别的公共配置

当一个 Service 中多个方法都使用同一个缓存区域时，可以使用 @CacheConfig：

~~~java
@Service
@CacheConfig(cacheNames = "products")
public class ProductService {

    @Cacheable(key = "#id")
    public Product getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

    @CacheEvict(key = "#id")
    public void deleteProduct(Long id) {
        // 通过 ProductMapper 中对应的 delete SQL 删除商品
    }
}
~~~

方法级别的配置优先级更高，可以覆盖类级别的默认配置。

## 十、查询、修改、删除的完整示例

下面是一个完整的 Service 示例：

~~~java
@Service
@CacheConfig(cacheNames = "products")
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    // 查询：命中直接返回，未命中时查询数据库并写入缓存
    @Cacheable(key = "#id")
    public Product getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

    // 更新数据库，并用最新返回值覆盖缓存
    @CachePut(key = "#id")
    public Product updateProduct(Long id, ProductRequest request) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        product.setName(request.name());
        product.setPrice(request.price());
        // 通过 ProductMapper 中对应的 update SQL 持久化商品
        return product;
    }

    // 删除数据库记录，同时删除详情缓存
    @CacheEvict(key = "#id")
    public void deleteProduct(Long id) {
        // 通过 ProductMapper 中对应的 delete SQL 删除商品
    }
}
~~~

推荐的请求流程：

~~~text
查询：GET /api/products/1
  -> @Cacheable 检查 products::1
  -> 命中：直接返回
  -> 未命中：MySQL 查询 -> 写入 Redis -> 返回

更新：PUT /api/products/1
  -> 更新 MySQL
  -> @CachePut 用最新对象覆盖 products::1

删除：DELETE /api/products/1
  -> 删除 MySQL 数据
  -> @CacheEvict 删除 products::1
~~~

如果采用“修改数据库后删除缓存，再由查询回填”的策略，则更新方法使用 @CacheEvict 而不是 @CachePut。两种方案都常见，关键是统一项目策略并处理好一致性。

## 十一、验证命中与未命中

先删除旧缓存：

~~~bash
redis-cli -n 0 DEL products::1
~~~

第一次请求：

~~~bash
curl http://localhost:8080/api/products/1
~~~

预期：

- 控制台打印 CACHE_MISS。
- 方法体执行并查询 MySQL。
- Redis 出现 products::1。

第二次请求同一个 ID：

~~~bash
curl http://localhost:8080/api/products/1
~~~

预期：

- 不再打印 CACHE_MISS。
- 不进入 getProductById 方法体。
- 直接从 Redis 返回结果。

查看 key 和 TTL：

~~~bash
redis-cli -n 0 EXISTS products::1
redis-cli -n 0 TTL products::1
redis-cli -n 0 GET products::1
~~~

## 十二、常见问题

### 1. 加了 @Cacheable 仍然每次查询数据库

重点检查：

- 启动类是否添加 @EnableCaching。
- 是否引入 spring-boot-starter-cache。
- Redis 是否运行在配置的地址和端口。
- 当前运行环境是否激活 test profile。当前项目的 CacheConfig 使用 @Profile("!test")，测试环境不会使用真实 Redis 配置。
- 方法是否通过 Spring Bean 的代理调用。
- 缓存 key 是否每次一致。
- Redis 中的 key 是否已经过期或被删除。

### 2. 为什么在 Controller 中加 Thread.sleep，第二次请求仍然变慢

因为 Controller 方法在 Spring Cache 检查之前执行：

~~~text
进入 Controller -> sleep -> 调用 Service -> 检查缓存
~~~

如果想观察未命中慢、命中快，应把模拟耗时放在 @Cacheable 标注的方法体内：

~~~java
@Cacheable(cacheNames = "products", key = "#id")
public Product getProductById(Long id) {
    try {
        Thread.sleep(2000);
    } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("查询被中断", exception);
    }
    Product product = productMapper.selectById(id);
    if (product == null) {
        throw new ProductNotFoundException(id);
    }
    return product;
}
~~~

这段代码只适合本地演示，不应在生产代码中人为阻塞请求线程。

### 3. 为什么同一个类内部调用时缓存不生效

例如：

~~~java
public Product outer(Long id) {
    return getProductById(id); // 类内部直接调用，绕过 Spring 代理
}
~~~

@Cacheable 依赖 Spring 代理拦截。类内部通过 this 调用时没有经过代理，因此缓存注解可能不生效。常见做法是从另一个 Spring Bean 调用，或重新设计服务边界。

### 4. 修改数据库后还能读到旧数据

数据库和缓存是两套独立存储。修改数据库后，必须使用 @CachePut 写入新值，或使用 @CacheEvict 删除旧值，让下次查询重新加载。

### 5. 不存在的商品会不会反复查数据库

当前配置使用 disableCachingNullValues()，不存在的商品不会写入 null 缓存。因此同一个不存在的 ID 每次都会查询数据库，这可能产生缓存穿透压力。

常见防护方式：

- 对不存在的 ID 短时间缓存一个特殊空对象。
- 使用布隆过滤器挡住明显不存在的 key。
- 对接口参数做校验和限流。

### 6. @Cacheable 能代替所有 Redis 操作吗

不能。简单的查询缓存适合 @Cacheable，但以下场景通常需要 RedisTemplate、Lua 或 Redisson：

- 分布式锁。
- 计数器和限流。
- 延迟队列、发布订阅。
- 批量操作和复杂 Redis 数据结构。
- 需要精确控制命令顺序或原子性的场景。

## 十三、总结

可以记住下面这组关系：

~~~text
spring-boot-starter-cache
  = Spring Cache 抽象、缓存注解、缓存代理

spring-boot-starter-data-redis
  = Redis 客户端、Redis 连接、RedisCacheManager

@Cacheable
  = 有缓存就直接返回，没有缓存才执行方法并回填

@CachePut
  = 每次执行方法，并用返回值更新缓存

@CacheEvict
  = 删除指定缓存或整个缓存区域

@Caching
  = 组合多个缓存操作

@CacheConfig
  = 统一类级别缓存默认配置
~~~

对于当前商品详情查询，最典型的流程是：

~~~text
读：@Cacheable
写：@CachePut 或更新后 @CacheEvict
删：@CacheEvict
~~~

最终目标不是所有数据都放 Redis，而是在可接受的一致性、性能和复杂度之间取得平衡。
