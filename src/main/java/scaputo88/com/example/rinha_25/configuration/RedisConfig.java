package scaputo88.com.example.rinha_25.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


@Configuration
public class RedisConfig {

    @Value("${redis.host:redis}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.timeout.millis:1000}")
    private int timeoutMillis;

    @Bean(destroyMethod = "close")
    public JedisPool jedisPool() {
        JedisPoolConfig pool = new JedisPoolConfig();
        pool.setMaxTotal(64);
        pool.setMaxIdle(16);
        pool.setMinIdle(2);
        pool.setTestOnBorrow(true);
        pool.setTestWhileIdle(true);

        // Conexão TCP
        return new JedisPool(pool, redisHost, redisPort, timeoutMillis);
    }
}



