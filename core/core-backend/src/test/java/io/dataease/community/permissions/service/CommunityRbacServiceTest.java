package io.dataease.community.permissions.service;

import io.dataease.auth.bo.TokenUserBO;
import io.dataease.community.permissions.dao.entity.CommunityRole;
import io.dataease.community.permissions.dao.entity.CommunityUser;
import io.dataease.community.permissions.dao.entity.CommunityUserRole;
import io.dataease.community.permissions.dao.mapper.CommunityRoleMapper;
import io.dataease.community.permissions.dao.mapper.CommunityRoleMenuMapper;
import io.dataease.community.permissions.dao.mapper.CommunityUserMapper;
import io.dataease.community.permissions.dao.mapper.CommunityUserRoleMapper;
import io.dataease.community.permissions.security.CommunityForbiddenException;
import io.dataease.exception.DEException;
import io.dataease.menu.dao.auto.mapper.CoreMenuMapper;
import io.dataease.utils.AuthUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityRbacServiceTest {

    @Mock
    private CommunityUserMapper userMapper;
    @Mock
    private CommunityRoleMapper roleMapper;
    @Mock
    private CommunityUserRoleMapper userRoleMapper;
    @Mock
    private CommunityRoleMenuMapper roleMenuMapper;
    @Mock
    private CoreMenuMapper menuMapper;

    private CommunityRbacService service;

    @BeforeEach
    void setUp() {
        service = new CommunityRbacService(userMapper, roleMapper, userRoleMapper, roleMenuMapper, menuMapper);
    }

    @AfterEach
    void tearDown() {
        AuthUtils.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void missingAccountReturnsGenericLoginError() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate("missing-user", "AnyPassword@1"))
                .isInstanceOfSatisfying(DEException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo(20002);
                    org.assertj.core.api.Assertions.assertThat(exception.getMessage()).isEqualTo("用户名或密码错误");
                });
    }

    @Test
    void nonAdminCannotCallAdministrativeOperations() {
        AuthUtils.setUser(new TokenUserBO(2L, 1L));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(service::requireAdmin)
                .isInstanceOf(CommunityForbiddenException.class)
                .hasMessageContaining("系统管理员");
    }

    @Test
    void shareTokenCannotUseAdministratorIdentity() {
        TokenUserBO admin = new TokenUserBO(1L, 1L);
        admin.setSysAdmin(true);
        AuthUtils.setUser(admin);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DE-LINK-TOKEN", "share-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(service::requireAdmin)
                .isInstanceOf(CommunityForbiddenException.class);
    }

    @Test
    void ordinaryUserCannotAssignRolesToSelf() {
        AuthUtils.setUser(new TokenUserBO(2L, 1L));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        io.dataease.api.permissions.user.dto.UserEditor editor = new io.dataease.api.permissions.user.dto.UserEditor();
        editor.setId(2L);
        editor.setRoleIds(List.of(10L));

        assertThatThrownBy(() -> service.editUser(editor, null))
                .isInstanceOf(CommunityForbiddenException.class);
        verify(userMapper, never()).updateById(any(CommunityUser.class));
    }

    @Test
    void changedAuthVersionInvalidatesExistingSession() {
        CommunityUser user = enabledUser(2L, 5L);
        when(userMapper.selectById(2L)).thenReturn(user);

        assertThatThrownBy(() -> service.validateSession(new TokenUserBO(2L, 1L), 4L))
                .isInstanceOf(DEException.class)
                .hasMessageContaining("重新登录");
    }

    @Test
    void disabledUserSessionIsRejected() {
        CommunityUser user = enabledUser(2L, 5L);
        user.setEnabled(false);
        when(userMapper.selectById(2L)).thenReturn(user);

        assertThatThrownBy(() -> service.validateSession(new TokenUserBO(2L, 1L), 5L))
                .isInstanceOf(DEException.class);
    }

    @Test
    void dashboardPermissionAlsoGrantsSharedScreenBackendOperations() {
        AuthUtils.setUser(new TokenUserBO(1L, 1L));
        CommunityUserRole adminRelation = new CommunityUserRole();
        adminRelation.setUserId(1L);
        adminRelation.setRoleId(10L);
        adminRelation.setOid(1L);
        CommunityRole normalRole = new CommunityRole();
        normalRole.setId(20L);
        normalRole.setOid(1L);
        normalRole.setSystemRole(false);
        io.dataease.menu.dao.auto.entity.CoreMenu panel = new io.dataease.menu.dao.auto.entity.CoreMenu();
        panel.setId(2L);
        io.dataease.menu.dao.auto.entity.CoreMenu screen = new io.dataease.menu.dao.auto.entity.CoreMenu();
        screen.setId(3L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(adminRelation), List.of());
        when(roleMapper.selectCount(any())).thenReturn(1L);
        when(roleMapper.selectOne(any())).thenReturn(normalRole);
        when(menuMapper.selectList(null)).thenReturn(List.of(panel, screen));
        io.dataease.api.permissions.auth.vo.PermissionItem permission =
                new io.dataease.api.permissions.auth.vo.PermissionItem();
        permission.setId(2L);
        permission.setWeight(1);

        service.saveRoleMenuPermissions(20L, List.of(permission));

        org.mockito.ArgumentCaptor<io.dataease.community.permissions.dao.entity.CommunityRoleMenu> captor =
                org.mockito.ArgumentCaptor.forClass(io.dataease.community.permissions.dao.entity.CommunityRoleMenu.class);
        verify(roleMenuMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getAllValues())
                .extracting(io.dataease.community.permissions.dao.entity.CommunityRoleMenu::getMenuId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void cannotUnmountLastEnabledAdministrator() {
        configureSingleAdministrator();

        assertThatThrownBy(() -> service.unmountUser(10L, 1L))
                .isInstanceOf(CommunityForbiddenException.class)
                .hasMessageContaining("至少一个");
        verify(userRoleMapper, never()).delete(any());
    }

    @Test
    void cannotDisableLastEnabledAdministrator() {
        configureSingleAdministrator();
        io.dataease.api.permissions.user.dto.EnableSwitchRequest request =
                new io.dataease.api.permissions.user.dto.EnableSwitchRequest();
        request.setId(1L);
        request.setEnable(false);

        assertThatThrownBy(() -> service.switchUserEnabled(request))
                .isInstanceOf(CommunityForbiddenException.class)
                .hasMessageContaining("至少一个");
        verify(userMapper, never()).updateById(any(CommunityUser.class));
    }

    @Test
    void cannotDeleteLastEnabledAdministrator() {
        configureSingleAdministrator();

        assertThatThrownBy(() -> service.deleteUser(1L))
                .isInstanceOf(CommunityForbiddenException.class)
                .hasMessageContaining("至少一个");
        verify(userMapper, never()).deleteById(1L);
    }

    private void configureSingleAdministrator() {
        AuthUtils.setUser(new TokenUserBO(1L, 1L));
        CommunityUserRole adminRelation = new CommunityUserRole();
        adminRelation.setUserId(1L);
        adminRelation.setRoleId(10L);
        adminRelation.setOid(1L);
        CommunityRole adminRole = new CommunityRole();
        adminRole.setId(10L);
        adminRole.setOid(1L);
        adminRole.setSystemRole(true);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(adminRelation));
        when(roleMapper.selectCount(any())).thenReturn(1L);
        lenient().when(roleMapper.selectOne(any())).thenReturn(adminRole);
        when(userMapper.selectById(1L)).thenReturn(enabledUser(1L, 2L));
        when(userMapper.lockEnabledAdminIds(1L)).thenReturn(List.of(1L));
    }

    private CommunityUser enabledUser(Long id, Long authVersion) {
        CommunityUser user = new CommunityUser();
        user.setId(id);
        user.setAccount(id == 1L ? "admin" : "user");
        user.setName("User");
        user.setEnabled(true);
        user.setDefaultOid(1L);
        user.setAuthVersion(authVersion);
        return user;
    }
}
