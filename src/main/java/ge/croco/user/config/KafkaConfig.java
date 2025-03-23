package ge.croco.user.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic usersTopic() {
        return TopicBuilder.name("users")
                .partitions(4)
                .replicas(1)
                .build();
    }
}