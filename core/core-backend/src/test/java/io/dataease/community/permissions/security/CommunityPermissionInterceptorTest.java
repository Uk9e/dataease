package io.dataease.community.permissions.security;

import io.dataease.auth.DeApiPath;
import io.dataease.auth.DePermit;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.constant.AuthResourceEnum;
import io.dataease.utils.AuthUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityPermissionInterceptorTest {

    private final CommunityRbacService service = mock(CommunityRbacService.class);
    private final CommunityPermissionInterceptor interceptor = new CommunityPermissionInterceptor(service);

    @AfterEach
    void tearDown() {
        AuthUtils.remove();
    }

    @Test
    void tokenFilterWhitelistMarkerBypassesUserPermissionChecks() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(io.dataease.constant.AuthConstant.WHITE_LISTED_ATTRIBUTE, Boolean.TRUE);
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod("query"));

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
    }

    @Test
    void requestWithoutAuthenticatedUserIsRejected() throws Exception {
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod("query"));

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler))
                .isInstanceOf(CommunityForbiddenException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    void unknownWriteEndpointIsDeniedByDefault() throws Exception {
        TokenUserBO user = new TokenUserBO(2L, 1L);
        AuthUtils.setUser(user);
        HandlerMethod handler = new HandlerMethod(new UnknownController(), UnknownController.class.getMethod("createThing"));

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest("POST", "/de2api/unknown"),
                new MockHttpServletResponse(), handler))
                .isInstanceOf(CommunityForbiddenException.class)
                .hasMessageContaining("默认拒绝");
    }

    @Test
    void directManageCallWithoutPermissionIsDenied() throws Exception {
        TokenUserBO user = new TokenUserBO(2L, 1L);
        AuthUtils.setUser(user);
        when(service.isAdmin(2L, 1L)).thenReturn(false);
        when(service.hasMenuPermission(2L, 1L, 6L, 2)).thenReturn(false);
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod("save"));

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler))
                .isInstanceOf(CommunityForbiddenException.class);
        verify(service).hasMenuPermission(2L, 1L, 6L, 2);
    }

    @Test
    void authorizedReadCallIsAllowed() throws Exception {
        TokenUserBO user = new TokenUserBO(2L, 1L);
        AuthUtils.setUser(user);
        when(service.isAdmin(2L, 1L)).thenReturn(false);
        when(service.hasMenuPermission(2L, 1L, 6L, 1)).thenReturn(true);
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod("query"));

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler)).isTrue();
    }

    static class UnknownController {
        public void createThing() {
        }
    }

    @DeApiPath(value = "/test", rt = AuthResourceEnum.DATASOURCE)
    interface TestApi {
        @DePermit("m:manage")
        void save();

        @DePermit("m:read")
        void query();
    }

    static class TestController implements TestApi {
        @Override
        public void save() {
        }

        @Override
        public void query() {
        }
    }
}
