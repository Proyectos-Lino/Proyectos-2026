package com.odontocare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class OdontoCareApplication {

  public static void main(String[] args) {
    SpringApplication.run(OdontoCareApplication.class, args);
  }
}
