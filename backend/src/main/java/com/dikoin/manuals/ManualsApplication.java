package com.dikoin.manuals;

import com.dikoin.manuals.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class ManualsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManualsApplication.class, args);
    }
}
