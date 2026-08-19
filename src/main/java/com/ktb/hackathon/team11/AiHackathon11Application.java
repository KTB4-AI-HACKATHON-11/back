package com.ktb.hackathon.team11;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class AiHackathon11Application {

  public static void main(String[] args) {
    SpringApplication.run(AiHackathon11Application.class, args);
  }
}
