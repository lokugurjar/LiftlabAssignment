package com.example.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AppConfig {

  @Value("${app.redis.host:localhost}") private String redisHost;
  @Value("${app.redis.port:6379}") private int redisPort;

  @Bean
  public LettuceConnectionFactory redisConnectionFactory() {
    return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
  }

  @Bean
  public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }
}
