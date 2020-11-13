package com.han.filter;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.nio.charset.Charset;
import java.util.List;

@Component
public class RedisBloomFilter {

    static final int expectedInsertions = 100;//要插入多少数据
    static final double fpp = 0.01;//期望的误判率

    //bit数组长度
    private static long numBits;

    //hash函数数量
    private static int numHashFunctions;

    @Autowired
    private RedisTemplate redisTemplate;

    static {
        numBits = optimalNumOfBits(expectedInsertions, fpp);
        numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
    }

    /**
     * 根据key获取bitmap下标
     */
    private static long[] getIndexs(String key) {
        long hash1 = hash(key);
        long hash2 = hash1 >>> 16;
        long[] result = new long[numHashFunctions];
        for (int i = 0; i < numHashFunctions; i++) {
            long combinedHash = hash1 + i * hash2;
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            result[i] = combinedHash % numBits;
        }
        return result;
    }

    private static long hash(String key) {
        Charset charset = Charset.forName("UTF-8");
        return Hashing.murmur3_128().hashObject(key, Funnels.stringFunnel(charset)).asLong();
    }

    //计算hash函数个数
    private static int optimalNumOfHashFunctions(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    //计算bit数组长度
    private static long optimalNumOfBits(long n, double p) {
        if (p == 0) {
            p = Double.MIN_VALUE;
        }
        return (long) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    /**
     * 设置key对应的Bloom过滤器位置为1
     *
     * @param bloomKey 存在缓存中的布隆过滤器的key
     * @param redisKey 查询redis数据的缓存key
     * @return
     */
    public List put(String bloomKey, String redisKey) {
        Assert.notNull(bloomKey, "存在缓存中的布隆过滤器的key不能为空");
        Assert.notNull(redisKey, "查询redis数据的缓存key不能为空");
        final long[] indexs = getIndexs(redisKey);
        final List list = redisTemplate.executePipelined((RedisCallback<Object>) redisConnection -> {
            redisConnection.openPipeline();
            for (long index : indexs) {
                final Boolean aBoolean = redisConnection.setBit(bloomKey.getBytes(), index, true);
            }
            redisConnection.close();
            return null;
        });
        return list;
    }

    /**
     * 通过Bloom过滤器判断key是否存在
     *
     * @param bloomKey 存在缓存中的布隆过滤器的key
     * @param redisKey 查询redis数据的缓存key
     * @return
     */
    public Boolean filterIsExistKey(String bloomKey, String redisKey) {
        Assert.notNull(bloomKey, "存在缓存中的布隆过滤器的key不能为空");
        Assert.notNull(redisKey, "查询redis数据的缓存key不能为空");
        final long[] indexs = getIndexs(redisKey);
        final List list = redisTemplate.executePipelined((RedisCallback<Object>) redisConnection -> {
            redisConnection.openPipeline();
            for (long index : indexs) {
                final Boolean aBoolean = redisConnection.getBit(bloomKey.getBytes(), index);
            }
            redisConnection.close();
            return null;
        });
        return !list.contains(false);
    }

    public static void main(String[] args) {
        //Jedis jedis = new Jedis("192.168.0.109", 6379);
        /*for (int i = 0; i < 100; i++) {
            long[] indexs = getIndexs(String.valueOf(i));
            for (long index : indexs) {
                //redisTemplate.opsForValue().setBit("user:bloom", index, true);
                //jedis.setbit("user:bloom", index, true);
            }
        }
        for (int i = 0; i < 100; i++) {
            long[] indexs = getIndexs(String.valueOf(i));
            for (long index : indexs) {
                final Boolean> isContain = redisTemplate.opsForValue().getBit("user:bloom", index);
                if (!isContain) {
                    System.out.println(i + "肯定没有重复");
                }
            }
            System.out.println(i + "可能重复");
        }*/
    }
}
