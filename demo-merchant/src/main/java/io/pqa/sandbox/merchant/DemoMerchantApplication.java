package io.pqa.sandbox.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(SandboxProperties.class)
@EnableScheduling
public class DemoMerchantApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoMerchantApplication.class, args);
    }
}
