package com.ktb.hackathon.team11.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI convenienceStoreTaskAgentOpenApi(
      @Value("${openapi.server-url:http://localhost:8080}") String serverUrl) {
    Info info =
        new Info()
            .title("편의점 업무 관리 AI 에이전트 API")
            .description("관리자의 자연어 업무를 반복 태스크로 생성하고 알바생의 수행 및 사진 인증을 관리하는 API")
            .version("v1")
            .contact(new Contact().name("KTB Hackathon Team 11"))
            .license(new License().name("Private Demo API"));

    return new OpenAPI()
        .info(info)
        .servers(List.of(new Server().url(serverUrl).description("API Server")))
        .components(new Components());
  }
}
