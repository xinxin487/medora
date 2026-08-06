package com.medora.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI medoraOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Medora API")
                        .version("0.9.0")
                        .description("Medora 智能医疗服务平台 — 后端 API 契约")
                        .contact(new Contact().name("Medora Team").email("dev@medora.local"))
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录获取的 accessToken，请求头: Authorization: Bearer <token>")));
    }
}
