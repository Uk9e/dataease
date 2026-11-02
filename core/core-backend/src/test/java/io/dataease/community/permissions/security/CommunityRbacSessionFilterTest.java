package io.dataease.community.permissions.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.exception.DEException;
import io.dataease.utils.AuthUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CommunityRbacSessionFilterTest {

    @AfterEach
    void tearDown() {
        AuthUtils.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void revokedPermissionReturnsUnauthorizedBeforeController() throws Exception {
        CommunityRbacService service = mock(CommunityRbacService.class);
        CommunityRbacSessionFilter filter = new CommunityRbacSessionFilter(service);
        TokenUserBO user = new TokenUserBO(2L, 1L);
        AuthUtils.setUser(user);
        String token = JWT.create().withClaim("uid", 2L).withClaim("oid", 1L)
                .withClaim("authVersion", 3L).sign(Algorithm.HMAC256("test-secret"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/de2api/role/query");
        request.addHeader("X-DE-TOKEN", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        FilterChain chain = mock(FilterChain.class);
        doThrow(new DEException(20001, "登录状态或权限已变更，请重新登录"))
                .when(service).validateSession(user, 3L);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("DE-GATEWAY-FLAG")).isEqualTo("permission-changed");
        verify(chain, never()).doFilter(request, response);
    }
}
