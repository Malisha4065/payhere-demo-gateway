package org.dushmin.payheredemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PayhereDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayhereDemoApplication.class, args);
    }

}
