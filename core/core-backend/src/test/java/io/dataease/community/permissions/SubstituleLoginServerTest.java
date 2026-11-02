package io.dataease.community.permissions;

import io.dataease.api.permissions.login.dto.PwdLoginDTO;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.exception.DEException;
import io.dataease.substitute.permissions.login.SubstituleLoginServer;
import io.dataease.utils.RsaUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class SubstituleLoginServerTest {

    @Test
    void staleRsaCiphertextReturnsRefreshMessage() {
        @SuppressWarnings("unchecked")
        ObjectProvider<CommunityRbacService> provider = mock(ObjectProvider.class);
        SubstituleLoginServer server = new SubstituleLoginServer(provider);
        PwdLoginDTO request = new PwdLoginDTO();
        request.setName("stale-ciphertext");
        request.setPwd("stale-ciphertext");

        try (MockedStatic<RsaUtils> rsaUtils = mockStatic(RsaUtils.class)) {
            rsaUtils.when(() -> RsaUtils.decryptStr(anyString())).thenThrow(new RuntimeException("bad padding"));

            assertThatThrownBy(() -> server.localLogin(request))
                    .isInstanceOfSatisfying(DEException.class, exception -> {
                        assertThat(exception.getCode()).isEqualTo(20002);
                        assertThat(exception.getMessage()).contains("刷新页面");
                    });
        }
    }
}
