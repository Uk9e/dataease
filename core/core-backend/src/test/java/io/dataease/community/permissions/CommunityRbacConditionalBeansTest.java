package io.dataease.community.permissions;

import io.dataease.community.permissions.server.CommunityAuthServer;
import io.dataease.community.permissions.server.CommunityRoleServer;
import io.dataease.community.permissions.service.CommunityRbacService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CommunityRbacConditionalBeansTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("dataease.community-rbac.enabled=true")
            .withBean(CommunityRbacService.class, () -> mock(CommunityRbacService.class))
            .withUserConfiguration(CommunityRoleServer.class, CommunityAuthServer.class);

    @Test
    void communityControllersLoadWhenNoXpackImplementationExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CommunityRoleServer.class);
            assertThat(context).hasSingleBean(CommunityAuthServer.class);
        });
    }
}
