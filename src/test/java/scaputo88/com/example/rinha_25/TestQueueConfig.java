package scaputo88.com.example.rinha_25;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@TestConfiguration
public class TestQueueConfig {

    @Bean
    public BlockingQueue<?> testQueue() {
        return new LinkedBlockingQueue<>();
    }
}
