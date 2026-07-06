package com.example.order.api;

import com.example.order.api.book.BookApiClient;
import com.example.order.api.point.PointApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ApiClientConfig {

    @Bean
    public BookApiClient bookApiClient() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));

        return new BookApiClient(
                RestClient.builder()
                        .requestFactory(factory)
                        .baseUrl("http://localhost:8082")
                        .build()
        );
    }

    @Bean
    public PointApiClient pointApiClient() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(1));

        return new PointApiClient(
                RestClient.builder()
                        .requestFactory(factory)
                        .baseUrl("http://localhost:8081")
                        .build()
        );
    }

}
