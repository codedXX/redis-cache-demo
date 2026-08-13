# Redis 分布式布隆过滤器设计

## 目标

为商品查询增加 Redis 分布式布隆过滤器，在商品不存在时尽早返回，降低恶意或高频不存在 ID 对 MySQL 的穿透压力。现有的 Spring Cache、MyBatis 查询和 HTTP 返回行为保持不变。

## 方案

- 使用 Redisson `RBloomFilter<Long>`，过滤器名称固定为 `products:bloom`。
- 应用启动后从 MySQL 查询全部商品 ID，并写入 Redis 布隆过滤器。
- `ProductService` 查询商品时先判断布隆过滤器：
  - 返回“确定不存在”：直接抛出 `ProductNotFoundException`，不访问 MySQL。
  - 返回“可能存在”：继续执行现有的 Spring Cache 和 MyBatis 查询，数据库作为最终事实来源。
- 布隆过滤器允许误判为“可能存在”，不允许因为过滤器误判而阻断真实商品访问。
- 新增商品时应调用 `ProductBloomFilter.add(id)`；当前项目没有新增商品接口，因此本次只提供可复用的写入能力。
- 删除商品不从布隆过滤器移除，避免普通布隆过滤器不支持删除造成的复杂性；删除后的 ID 只会多一次缓存/数据库校验，不会返回错误数据。

## 结构

- `filter/ProductBloomFilter`：业务抽象，隔离 Service 与 Redisson API。
- `filter/RedisProductBloomFilter`：基于 Redisson 的 Redis 实现。
- `filter/ProductBloomFilterInitializer`：启动时加载 `ProductMapper.selectAllIds()` 的商品 ID。
- `config/RedissonConfig`：根据现有 `spring.data.redis` 配置创建 Redisson 单节点客户端；Redis 本身可由哨兵、集群或代理统一提供分布式能力，过滤器数据仍存储在共享 Redis 中。

## 配置

```yaml
app:
  bloom-filter:
    name: products:bloom
    expected-insertions: 100000
    false-positive-probability: 0.01
```

`expected-insertions` 和误判率用于初始化过滤器容量。过滤器初始化使用 `tryInit`，重复启动不会覆盖已经存在的 Redis 过滤器。

## 测试

- Service 单元测试验证过滤器确定不存在时不调用 Mapper。
- Redis 过滤器单元测试验证初始化、写入和查询均委托给 `RBloomFilter`。
- Mapper 集成测试验证批量读取商品 ID。
- Controller 测试继续验证正常商品返回 200、缓存只查询一次，以及不存在商品返回 404。
