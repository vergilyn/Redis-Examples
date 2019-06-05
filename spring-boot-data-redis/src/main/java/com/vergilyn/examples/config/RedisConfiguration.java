package com.vergilyn.examples.config;

import java.lang.reflect.ParameterizedType;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import com.vergilyn.examples.listener.RedisExpiredListener;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * spring-boot-2.1.2.RELEASE默认使用的是lettuce。
 * <a href="https://docs.spring.io/spring-boot/docs/2.1.2.RELEASE/reference/htmlsingle/#howto-use-jedis-instead-of-lettuce">91.4 Use Jedis Instead of Lettuce<a/>
 */
@Configuration
@EnableTransactionManagement
public class RedisConfiguration<K, V> {

    /**
     * SDR默认采用的序列化策略有两种，一种是String的序列化策略，一种是JDK的序列化策略。
     * StringRedisTemplate默认采用的是String的序列化策略，保存的key和value都是采用此策略序列化保存的。
     * RedisTemplate默认采用的是JDK的序列化策略，保存的key和value都是采用此策略序列化保存的。
     * 就是因为序列化策略的不同，即使是同一个key用不同的Template去序列化，结果是不同的。所以根据key去删除数据的时候就出现了删除失败的问题。
     *
     * redis 序列化策略 ，通常情况下key值采用String序列化策略，
     * 如果不指定序列化策略，StringRedisTemplate的key和value都将采用String序列化策略；
     * 但是RedisTemplate的key和value都将采用JDK序列化 这样就会出现采用不同template保存的数据不能用同一个template删除的问题
     *
     */
    @Bean
    public StringRedisSerializer stringRedisSerializer(){
        return new StringRedisSerializer();
    }

    // @Bean
    public FastJsonRedisSerializer<K> fastJsonRedisSerializer(){
        Class clazz = (Class) ((ParameterizedType) RedisConfiguration.class.getGenericSuperclass()).getActualTypeArguments()[0];
        return new FastJsonRedisSerializer<K>(clazz);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory){
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(redisConnectionFactory);
        stringRedisTemplate.setEnableTransactionSupport(true); // spring-data-redis默认禁用事务支持
        return stringRedisTemplate;
    }

    /**
     * 解决redisTemplate的key/value乱码问题:
     *  <br/> <a href="http://www.zhimengzhe.com/shujuku/other/192111.html">解决Spring Boot 使用RedisTemplate 存储键值出现乱码 </a>
     *  <br/> <a href="http://blog.csdn.net/tianyaleixiaowu/article/details/70595073">Springboot中使用redis，配置redis的key value生成策略</a>
     * @return
     */
    @Bean
    public RedisTemplate<K, V> redisTemplate(RedisConnectionFactory redisConnectionFactory, StringRedisSerializer stringRedisSerializer){
        RedisTemplate<K, V> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // redisTemplate.setDefaultSerializer(redisSerializer);
        // redisTemplate.setKeySerializer(RedisSerializer.string());
        // redisTemplate.setValueSerializer(redisSerializer);
        // redisTemplate.setXxxSerializer();

        return redisTemplate;
    }

    @Bean
    public RedisMessageListenerContainer listenerContainer(RedisConnectionFactory connectionFactory, Executor executor){
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // 设置Redis的连接工厂
        container.setConnectionFactory(connectionFactory);
        // 设置监听使用的线程池
        container.setTaskExecutor(executor);
        // 设置监听的Topic: PatternTopic/ChannelTopic
        // 设置监听器
        container.addMessageListener(new RedisExpiredListener(), new PatternTopic(RedisExpiredListener.LISTENER_PATTERN));
        return container;
    }

    @Bean
    public Executor executor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("vergilyn-Thread");

        // rejection-policy：当pool已经达到max size的时候，如何处理新任务
        // CALLER_RUNS：不在新线程中执行任务，而是由调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
