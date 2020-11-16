package com.han.controller;

import com.han.lock.RedisLock;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * redis分布式锁的使用
 */
@RestController
public class RedisController {
    private static final String productStockLockKey = "product:stock:lock:100";
    private static final String productStockKey = "product:stock:100";
    private static final Integer TIMEOUT = 1000 * 10;
    private Logger logger = LoggerFactory.getLogger(RedisController.class);
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    RedisLock redisLock;
    @Autowired
    Redisson redisson;

    /**
     * 加锁第一种：多线程下不安全，会产生库存多扣除情况，出现超卖
     *
     * @return
     */
    @RequestMapping("/deductStock")
    public String deductStock() {

        int productStock = Integer.parseInt(stringRedisTemplate.opsForValue().get(productStockKey));
        if (productStock > 0) {
            productStock = productStock - 1;
            stringRedisTemplate.opsForValue().set(productStockKey, productStock + "");
            logger.error("【{}】商品扣减库成功，当前可用库存为【{}】", productStockKey, productStock);
        } else {
            logger.error("【{}】商品扣减库存失败", productStockKey);
        }
        return "end";
    }

    /**
     * 加锁第二种：使用redis的setIfAbsent进行加锁key设置
     * 注意：出现宕机，会出现productStockLockKey无法删除，导致后面的线程无法执行上面的程序
     *
     * @return
     */
    @RequestMapping("/deductStock2")
    public String deductStock2() {
        long time = System.currentTimeMillis() + TIMEOUT;
        final String lockTime = String.valueOf(time);
        try {
            final boolean lock = redisLock.lock(productStockLockKey, lockTime);
            if (!lock) {
                return "哎哟喂，人太多，请稍后再试~~";
            }
            int productStock = Integer.parseInt(stringRedisTemplate.opsForValue().get(productStockKey));
            if (productStock > 0) {
                productStock = productStock - 1;
                stringRedisTemplate.opsForValue().set(productStockKey, productStock + "");
                logger.info("【{}】商品扣减库成功，当前可用库存为【{}】", productStockKey, productStock);
            } else {
                logger.error("【{}】商品扣减库存失败", productStockKey);
            }
            //当程序执行到此处之前宕机，会出现productStockLockKey无法删除，导致后面的线程无法执行上面的程序
        } finally {
            //当程序执行到此处之前宕机，会出现productStockLockKey无法删除，导致后面的线程无法执行上面的程序
            redisLock.unlock(productStockLockKey, lockTime);
        }
        return "end";
    }

    /**
     * 加锁第三种：使用redis的setIfAbsent进行加锁key设置
     * 注意：设置超时时间，多线程情况下执行会导致key被其他线程误执行删除（当其他线程执行时间快的时候）
     *
     * @return
     */
    @RequestMapping("/deductStock3")
    public String deductStock3() {
        long time = System.currentTimeMillis() + TIMEOUT;
        final String lockTime = String.valueOf(time);
        try {
            //不是原子性
            /*final boolean lock = redisLock.lock(productStockLockKey, lockTime);
            //当程序执行到此处之前宕机，会出现productStockLockKey无法删除，导致后面的线程无法执行上面的程序
            stringRedisTemplate.expire(productStockLockKey,TIMEOUT, TimeUnit.SECONDS);//设置超时时间*/
            //原子性，但是又有新的问题，当下面的业务执行时间超过key设置的超时时间，多线程情况下执行会导致key被其他线程执行删除（当其他线程执行时间快的时候）
            final boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(productStockLockKey, lockTime, TIMEOUT, TimeUnit.SECONDS);
            if (!lock) {
                return "哎哟喂，人太多，请稍后再试~~";
            }
            int productStock = Integer.parseInt(stringRedisTemplate.opsForValue().get(productStockKey));
            if (productStock > 0) {
                productStock = productStock - 1;
                stringRedisTemplate.opsForValue().set(productStockKey, productStock + "");
                logger.info("【{}】商品扣减库成功，当前可用库存为【{}】", productStockKey, productStock);
            } else {
                logger.error("【{}】商品扣减库存失败", productStockKey);
            }
        } finally {
            stringRedisTemplate.delete(productStockKey);
        }
        return "end";
    }

    /**
     * 最佳加锁实现
     * 加锁第四种：使用redisson进行加锁key设置
     * 使用lua脚本执行解决续命问题，能保证原子性
     * 注意：不能执行任务时间太长或者死循环中使用
     *
     * @return
     */
    @RequestMapping("/deductStock4")
    public String deductStock4() {
        final RLock redissonLock = redisson.getLock(productStockLockKey);
        try {
            if (null == redissonLock) {
                return "哎哟喂，人太多，请稍后再试~~";
            }
            //加锁，实现续命（默认时间30秒）
            redissonLock.lock();
            int productStock = Integer.parseInt(stringRedisTemplate.opsForValue().get(productStockKey));
            if (productStock > 0) {
                productStock = productStock - 1;
                stringRedisTemplate.opsForValue().set(productStockKey, productStock + "");
                logger.info("【{}】商品扣减库成功，当前可用库存为【{}】", productStockKey, productStock);
            } else {
                logger.error("【{}】商品扣减库存失败", productStockKey);
            }
        } finally {
            redissonLock.unlock();
        }
        return "end";
    }

    /**
     * 最佳加锁实现(基于redisson读写锁，读读不互斥（相当于无锁），读写互斥)
     * 加锁第五种：使用redisson进行加锁key设置
     * 使用lua脚本执行解决续命问题，能保证原子性
     * 注意：不能执行任务时间太长或者死循环中使用
     *
     * @return
     */
    @RequestMapping("/readLock")
    public String readLock() {
        final RReadWriteLock readWriteLock = redisson.getReadWriteLock(productStockLockKey);
        if (null == readWriteLock) {
            return "哎哟喂，人太多，请稍后再试~~";
        }
        final RLock rLock = readWriteLock.readLock();
        try {
            //加锁，实现续命（默认时间30秒）
            int productStock = Integer.parseInt(stringRedisTemplate.opsForValue().get(productStockKey));
            if (productStock > 0) {
                productStock = productStock - 1;
                stringRedisTemplate.opsForValue().set(productStockKey, productStock + "");
                logger.info("【{}】商品扣减库成功，当前可用库存为【{}】", productStockKey, productStock);
            } else {
                logger.error("【{}】商品扣减库存失败", productStockKey);
            }
        } finally {
            rLock.unlock();
        }
        return "end";
    }


    /**
     * 最佳加锁实现(基于redisson读写锁，读读不互斥（相当于无锁），读写互斥)
     * 加锁第五种：使用redisson进行加锁key设置
     * 使用lua脚本执行解决续命问题，能保证原子性
     * 注意：不能执行任务时间太长或者死循环中使用
     *
     * @return
     */
    @RequestMapping("/writeLock")
    public String writeLock() {
        final RReadWriteLock readWriteLock = redisson.getReadWriteLock(productStockLockKey);
        if (null == readWriteLock) {
            return "哎哟喂，人太多，请稍后再试~~";
        }
        final RLock rLock = readWriteLock.writeLock();
        try {
            //加锁，实现续命（默认时间30秒）
            int productStock = Integer.parseInt(stringRedisTemplate.opsForValue().get(productStockKey));
            if (productStock > 0) {
                productStock = productStock - 1;
                stringRedisTemplate.opsForValue().set(productStockKey, productStock + "");
                logger.info("【{}】商品扣减库成功，当前可用库存为【{}】", productStockKey, productStock);
            } else {
                logger.error("【{}】商品扣减库存失败", productStockKey);
            }
        } finally {
            rLock.unlock();
        }
        return "end";
    }
}
