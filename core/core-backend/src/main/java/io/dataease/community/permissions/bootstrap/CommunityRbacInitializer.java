package io.dataease.community.permissions.bootstrap;

import io.dataease.community.permissions.service.CommunityRbacService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dataease.community-rbac.enabled", havingValue = "true")
public class CommunityRbacInitializer implements ApplicationRunner {

    private final CommunityRbacService rbacService;

    public CommunityRbacInitializer(CommunityRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public void run(ApplicationArguments args) {
        rbacService.bootstrap();
    }
}
