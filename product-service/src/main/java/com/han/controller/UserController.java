package com.han.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.han.common.Result;
import com.han.dto.NullObjectResult;
import com.han.entity.User;
import com.han.filter.RedisBloomFilter;
import com.han.service.IUserService;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class UserController {
    private static final String USER_KEY = "user:";
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    RedisBloomFilter redisBloomFilter;
    @Autowired
    Redisson redisson;

    /**
     * 通过查询数据库获取用户信息（直接查询mysql获取用户信息）
     *
     * @param userId
     * @return
     */
    @GetMapping("/findUserById")
    public Result findUserById(Integer userId) {
        final User user = userService.findUserByUserId(userId);
        return Result.SUCCESS(user);
    }

    /**
     * 通过缓存获取用户信息
     * 获取用户信息（如果缓存中有数据，就从缓存中获取，没有则查询数据库在放入缓存中）
     * 高并发场景下会出现以下问题：
     * 1、缓存穿透：缓存中没有查询到数据，数据中也没有查询到数据，每次访问都会去查询数据库，会给数据带来压力
     * 2、缓存击穿：缓存中没有，数据库中有，当key在缓存中过期时，此时若有大量并发请求过来，会去查询数据库再设置到缓存中，大并发的请求可能会瞬间把后端DB压垮
     * 3、缓存雪崩：当缓存服务器重启或者大量缓存集中在某一个时间段失效，这样在失效的时候，需要重新查询数据库数据再放入缓存中，也会给后端系统(比如DB)带来很大压力。
     *
     * @param userId
     * @return
     */
    @GetMapping("/findUserByCache")
    public Result findUserByCache(Integer userId) {
        String userKey = USER_KEY + userId;
        final String userString = stringRedisTemplate.opsForValue().get(userKey);
        if (!StringUtils.isEmpty(userString)) {
            final User user = JSONObject.toJavaObject(JSON.parseObject(userString), User.class);
            return Result.SUCCESS(user);
        }
        final User user = userService.findUserByUserId(userId);
        if (null != user) {
            stringRedisTemplate.opsForValue().set(userKey, JSONObject.toJSONString(user), 5, TimeUnit.SECONDS);
            return Result.SUCCESS(user);
        } else {
            return Result.FILE("用户【" + userId + "】信息不存在");
        }
    }

    /**
     * 通过设置一个空对象解决缓存穿透
     * 获取用户信息（如果缓存中有数据，就从缓存中获取，没有则查询数据库在放入缓存中）
     * 高并发场景下会出现以下问题：
     * 1、缓存穿透：缓存中没有查询到数据，数据中也没有查询到数据，每次访问都会去查询数据库，会给数据带来压力
     * 解决方案：
     * 1、设置一个空对象结果返回，但是解决不了根本问题，会造成缓存中存在好多空对象，占据内存，造成内存浪费
     * 2、通过布隆过滤器过滤请求，防止频繁查询数据库
     *
     * @param userId
     * @return
     */
    @GetMapping("/findUserByCachePassNull")
    public Result findUserByCachePassNull(Integer userId) {
        String userKey = USER_KEY + userId;
        final Object object = redisTemplate.opsForValue().get(userKey);
        if (!StringUtils.isEmpty(object)) {
            if (object instanceof NullObjectResult) {
                return Result.FILE(1001, "用户【" + userId + "】信息不存在,返回空对象");
            }
            return Result.SUCCESS(object);
        }
        final User user = userService.findUserByUserId(userId);
        if (null != user) {
            redisTemplate.opsForValue().set(userKey, user, 5, TimeUnit.SECONDS);
            return Result.SUCCESS(user);
        } else {
            redisTemplate.opsForValue().set(userKey, new NullObjectResult(), 20, TimeUnit.SECONDS);
        }
        return Result.FILE("用户【" + userId + "】信息不存在");
    }

    /**
     * 通过布隆过滤器解决缓存穿透
     * 获取用户信息（如果缓存中有数据，就从缓存中获取，没有则查询数据库在放入缓存中）
     * 高并发场景下会出现以下问题：
     * 1、缓存穿透：缓存中没有查询到数据，数据中也没有查询到数据，每次访问都会去查询数据库，会给数据带来压力
     * 解决方案：
     * 1、设置一个空对象结果返回，但是解决不了根本问题，会造成缓存中存在好多空对象，占据内存，造成内存浪费
     * 2、通过布隆过滤器过滤请求，防止频繁查询数据库
     *
     * @param userId
     * @return
     */
    @GetMapping("/findUserByCachePassBloomFilter")
    public Result findUserByCachePassBloomFilter(Integer userId) {
        String userKey = USER_KEY + userId;
        //使用布隆过滤器进行过滤
        if (!redisBloomFilter.filterIsExistKey("user:bloom", userId + "")) {
            return Result.FILE(1002, "经过布隆过滤器过滤之后，该用户【" + userId + "】信息不存在");
        }
        final Object object = redisTemplate.opsForValue().get(userKey);
        if (!StringUtils.isEmpty(object)) {
            if (object instanceof NullObjectResult) {
                return Result.FILE(1001, "用户【" + userId + "】信息不存在,返回空对象");
            }
            return Result.SUCCESS(object);
        }
        final User user = userService.findUserByUserId(userId);
        if (null != user) {
            redisTemplate.opsForValue().set(userKey, user, 5, TimeUnit.SECONDS);
            return Result.SUCCESS(user);
        } else {
            redisTemplate.opsForValue().set(userKey, new NullObjectResult(), 20, TimeUnit.SECONDS);
        }
        return Result.FILE("用户【" + userId + "】信息不存在");
    }

    /**
     * 通过布隆过滤器解决缓存击穿
     * 获取用户信息（缓存中没有，数据库中有，当key在缓存中过期时，此时若有大量并发请求过来，会去查询数据库再设置到缓存中，大并发的请求可能会瞬间把后端DB压垮）
     * 利用加分布式锁和双重检查策略解决
     *
     * @param userId
     * @return
     */
    @GetMapping("/findUserByCachePassBloomFilterSolveBreakdown")
    public Result findUserByCachePassBloomFilterSolveBreakdown(Integer userId) {
        String userKey = USER_KEY + userId;
        //使用布隆过滤器进行过滤
        if (!redisBloomFilter.filterIsExistKey("user:bloom", userId + "")) {
            return Result.FILE(1002, "经过布隆过滤器过滤之后，该用户【" + userId + "】信息不存在");
        }
        final RLock redissonLock = redisson.getLock(userKey);

        if (null == redissonLock) {
            return Result.FILE(1003, "服务器繁忙，请稍后再试！！！");
        }
        Object object = redisTemplate.opsForValue().get(userKey);
        if (!StringUtils.isEmpty(object)) {
            if (object instanceof NullObjectResult) {
                return Result.FILE(1001, "用户【" + userId + "】信息不存在,返回空对象");
            }
            return Result.SUCCESS(object);
        }
        try {
            //加锁,防止一个key过期时，多个请求过来查询key会对通一条数据进行多次查询
            redissonLock.lock();
            object = redisTemplate.opsForValue().get(userKey);
            if (!StringUtils.isEmpty(object)) {
                if (object instanceof NullObjectResult) {
                    return Result.FILE(1001, "用户【" + userId + "】信息不存在,返回空对象");
                }
                return Result.SUCCESS(object);
            }
            final User user = userService.findUserByUserId(userId);
            if (null != user) {
                redisTemplate.opsForValue().set(userKey, user, 5, TimeUnit.SECONDS);
                return Result.SUCCESS(user);
            } else {
                redisTemplate.opsForValue().set(userKey, new NullObjectResult(), 20, TimeUnit.SECONDS);
            }
            return Result.FILE("用户【" + userId + "】信息不存在");
        } finally {
            if (null != redissonLock) {
                redissonLock.unlock();
            }
        }
    }

    /**
     * 设置用户布隆过滤器的值
     */
    @RequestMapping("/putBloomData")
    public void putBloomData() {
        List<Integer> userIds = this.userService.findAllUserIds();
        if (!CollectionUtils.isEmpty(userIds)) {
            userIds.stream().forEach(userId -> {
                redisBloomFilter.put("user:bloom", userId + "");
            });
        }
    }
}

