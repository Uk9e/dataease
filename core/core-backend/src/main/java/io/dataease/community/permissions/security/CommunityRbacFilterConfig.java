package io.dataease.community.permissions.security;

import io.dataease.community.permissions.service.CommunityRbacService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "dataease.community-rbac.enabled", havingValue = "true")
public class CommunityRbacFilterConfig {

    @Bean
    public FilterRegistrationBean<CommunityRbacSessionFilter> communityRbacSessionFilter(CommunityRbacService rbacService) {
        FilterRegistrationBean<CommunityRbacSessionFilter> registration = new FilterRegistrationBean<>();
        registration.setName("communityRbacSessionFilter");
        registration.setFilter(new CommunityRbacSessionFilter(rbacService));
        registration.addUrlPatterns("/*");
        registration.setOrder(6);
        return registration;
    }
}
