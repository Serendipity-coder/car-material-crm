package com.checa.crm.config;

import com.checa.crm.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor interceptor;
    public WebConfig(AuthInterceptor interceptor){this.interceptor=interceptor;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(interceptor);}
    @Override public void addCorsMappings(CorsRegistry registry){registry.addMapping("/**").allowedOrigins("http://localhost:3000","http://127.0.0.1:3000").allowedMethods("GET","POST","PUT","DELETE","PATCH","OPTIONS").allowedHeaders("*").allowCredentials(true).maxAge(3600);}
}
