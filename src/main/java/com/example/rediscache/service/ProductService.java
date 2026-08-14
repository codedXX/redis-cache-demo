// 声明当前类所在的业务服务包。
package com.example.rediscache.service;

// 导入商品实体类，用于返回商品详情。
import com.example.rediscache.pojo.Product;
// 导入商品不存在异常，用于在商品不存在时返回明确错误。
import com.example.rediscache.exception.ProductNotFoundException;
// 导入商品数据库 Mapper，用于查询 MySQL。
import com.example.rediscache.mapper.ProductMapper;
// 导入布隆过滤器抽象，用于快速判断商品 ID 是否可能存在。
import com.example.rediscache.filter.ProductBloomFilter;
// 导入时间单位，用于配置分布式锁的等待时间和租约时间。
import java.util.concurrent.TimeUnit;
// 导入 Redisson 分布式锁接口。
import org.redisson.api.RLock;
// 导入 Redisson 客户端，用于获取商品对应的分布式锁。
import org.redisson.api.RedissonClient;
// 导入配置值注解，用于读取 application.yml 中的配置。
import org.springframework.beans.factory.annotation.Value;
// 导入 Spring Cache 缓存接口。
import org.springframework.cache.Cache;
// 导入 Spring Cache 管理器。
import org.springframework.cache.CacheManager;
// 导入日志对象。
import org.slf4j.Logger;
// 导入日志工厂。
import org.slf4j.LoggerFactory;
// 导入 Spring Service 注解。
import org.springframework.stereotype.Service;

// 声明这是 Spring 管理的商品业务服务。
@Service
public class ProductService {

    // 创建当前类的日志对象，用于记录缓存未命中和数据库回源信息。
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    // 保存商品 Mapper，用于访问商品数据库表。
    private final ProductMapper productMapper;
    // 保存布隆过滤器，用于在访问数据库前过滤明显不存在的商品 ID。
    private final ProductBloomFilter productBloomFilter;
    // 保存缓存管理器，用于读取和写入 products 缓存。
    private final CacheManager cacheManager;
    // 保存 Redisson 客户端，用于创建分布式互斥锁。
    private final RedissonClient redissonClient;
    // 保存请求等待锁的最长时间，单位为毫秒。
    private final long lockWaitTimeMs;
    // 保存锁的租约时间，单位为毫秒。
    private final long lockLeaseTimeMs;
    // 保存是否启用缓存击穿互斥锁的开关。
    private final boolean mutexEnabled;

    // 构造商品服务，并注入数据库、布隆过滤器、缓存和分布式锁依赖。
    public ProductService(ProductMapper productMapper, ProductBloomFilter productBloomFilter,
                          CacheManager cacheManager, RedissonClient redissonClient,
                          @Value("${app.cache.mutex.wait-time-ms:3000}") long lockWaitTimeMs,
                          @Value("${app.cache.mutex.lease-time-ms:10000}") long lockLeaseTimeMs,
                          @Value("${app.cache.mutex.enabled:true}") boolean mutexEnabled) {
        // 保存商品数据库 Mapper。
        this.productMapper = productMapper;
        // 保存商品布隆过滤器。
        this.productBloomFilter = productBloomFilter;
        // 保存 Spring Cache 管理器。
        this.cacheManager = cacheManager;
        // 保存 Redisson 客户端。
        this.redissonClient = redissonClient;
        // 保存锁等待时间配置。
        this.lockWaitTimeMs = lockWaitTimeMs;
        // 保存锁租约时间配置。
        this.lockLeaseTimeMs = lockLeaseTimeMs;
        // 保存互斥锁启用状态。
        this.mutexEnabled = mutexEnabled;
    }

    // 查询商品详情，并在缓存未命中时执行安全的数据库回源。
    public Product getProductById(Long id) {
        // 根据 products 名称获取商品缓存区域。
        Cache cache = cacheManager.getCache("products");
        // 第一次检查缓存，命中时不访问锁和数据库。
        Product cached = cache == null ? null : cache.get(id, Product.class);
        // 如果缓存命中，直接返回缓存中的商品。
        if (cached != null) {
            // 返回缓存商品，结束本次请求。
            return cached;
        }

        // 关闭互斥锁时直接回源，用于对比缓存击穿现象。
        if (!mutexEnabled) {
            // 不加锁查询数据库并把查询结果写回缓存。
            return loadAndCache(id, cache);
        }

        // 按商品 ID 获取独立锁，不同商品之间不会互相阻塞。
        RLock lock = redissonClient.getLock("lock:product:" + id);
        // 记录当前线程是否成功持有锁，便于 finally 中安全释放。
        boolean locked = false;
        // 使用 try/finally 确保异常时也能释放当前线程持有的锁。
        try {
            // 在等待时间内尝试获取锁，并设置锁的最大租约时间。
            locked = lock.tryLock(lockWaitTimeMs, lockLeaseTimeMs, TimeUnit.MILLISECONDS);
            // 如果没有获取到锁，说明其他线程可能正在重建缓存。
            if (!locked) {
                // 等待结束后再次读取缓存，优先使用其他线程已经回填的数据。
                Product rebuilt = cache == null ? null : cache.get(id, Product.class);
                // 如果缓存已经回填，直接返回重建后的商品。
                if (rebuilt != null) {
                    // 返回其他线程写入的缓存商品。
                    return rebuilt;
                }
                // 等锁后缓存仍为空时，不绕过锁访问数据库，直接报告重建超时。
                throw new IllegalStateException("缓存重建等待超时，productId=" + id);
            }

            // 获取锁后第二次检查缓存，避免重复回源数据库。
            cached = cache == null ? null : cache.get(id, Product.class);
            // 如果二次检查命中，说明等待期间已有线程完成缓存回填。
            if (cached != null) {
                // 返回二次检查得到的缓存商品。
                return cached;
            }

            // 只有持锁且二次检查仍未命中的线程才允许访问数据库。
            return loadAndCache(id, cache);
        } catch (InterruptedException e) {
            // 恢复当前线程的中断状态，避免吞掉线程中断信号。
            Thread.currentThread().interrupt();
            // 将锁等待中断转换为运行时异常并继续向上抛出。
            throw new IllegalStateException("等待商品缓存锁时被中断，productId=" + id, e);
        } finally {
            // 只有当前线程确实持有锁时才释放，避免误释放其他线程的锁。
            if (locked && lock.isHeldByCurrentThread()) {
                // 释放商品对应的分布式锁。
                lock.unlock();
            }
        }
    }

    // 查询数据库并将商品结果写入缓存。
    private Product loadAndCache(Long id, Cache cache) {
        // 先使用布隆过滤器过滤确定不存在的商品，避免无效数据库查询。
        if (!productBloomFilter.mightContain(id)) {
            // 布隆过滤器确认不存在时抛出商品不存在异常。
            throw new ProductNotFoundException(id);
        }

        // 使用人为延迟模拟数据库查询耗时，便于观察缓存击穿效果。
        try {
            // 暂停两秒，制造并发请求重叠窗口。
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // 恢复当前线程的中断状态。
            Thread.currentThread().interrupt();
            // 将数据库查询中断转换为运行时异常。
            throw new IllegalStateException("商品查询被中断", e);
        }

        // 记录缓存未命中以及即将访问数据库的日志。
        log.info("CACHE_MISS: Redis 未命中，开始查询 MySQL，productId={}", id);
        // 根据商品 ID 查询数据库。
        Product product = productMapper.selectById(id);
        // 数据库也没有商品时，抛出商品不存在异常。
        if (product == null) {
            // 返回商品不存在错误，不写入空值缓存。
            throw new ProductNotFoundException(id);
        }

        // 只有缓存对象存在时才回填缓存。
        if (cache != null) {
            // 将数据库查询结果写入 products 缓存。
            cache.put(id, product);
        }

        // 返回数据库查询并缓存后的商品对象。
        return product;
    }
}
