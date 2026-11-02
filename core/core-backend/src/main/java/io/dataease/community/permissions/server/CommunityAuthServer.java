package io.dataease.community.permissions.server;

import io.dataease.api.permissions.auth.api.AuthApi;
import io.dataease.api.permissions.auth.dto.BusiPerEditor;
import io.dataease.api.permissions.auth.dto.BusiPermissionRequest;
import io.dataease.api.permissions.auth.dto.BusiTargetPerCreator;
import io.dataease.api.permissions.auth.dto.MenuPerEditor;
import io.dataease.api.permissions.auth.dto.MenuPermissionRequest;
import io.dataease.api.permissions.auth.dto.MenuTargetPerCreator;
import io.dataease.api.permissions.auth.vo.PermissionVO;
import io.dataease.api.permissions.auth.vo.ResourceItemVO;
import io.dataease.api.permissions.auth.vo.ResourceVO;
import io.dataease.community.permissions.CommunityRbacConstants;
import io.dataease.community.permissions.dao.entity.CommunityRole;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.exception.DEException;
import io.dataease.i18n.Translator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("communityAuthServer")
@RequestMapping("/auth")
@ConditionalOnMissingBean(name = "authServer")
@ConditionalOnProperty(name = "dataease.community-rbac.enabled", havingValue = "true")
public class CommunityAuthServer implements AuthApi {

    private final CommunityRbacService rbacService;

    public CommunityAuthServer(CommunityRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public List<ResourceVO> busiResource(String flag) {
        unsupportedBusinessPermission();
        return List.of();
    }

    @Override
    public PermissionVO busiPermission(BusiPermissionRequest request) {
        unsupportedBusinessPermission();
        return null;
    }

    @Override
    public PermissionVO busiTargetPermission(BusiPermissionRequest request) {
        unsupportedBusinessPermission();
        return null;
    }

    @Override
    public List<ResourceVO> menuResource() {
        rbacService.requireAdmin();
        return rbacService.authorizableMenus().stream().map(menu -> {
            ResourceVO resource = new ResourceVO();
            resource.setId(menu.getId());
            resource.setName(menu.getId() == 2L
                    ? Translator.get("i18n_menu.panel") + " / " + Translator.get("i18n_menu.screen")
                    : Translator.get("i18n_menu." + menu.getName()));
            resource.setLeaf(true);
            resource.setChildren(List.of());
            return resource;
        }).toList();
    }

    @Override
    public PermissionVO menuPermission(MenuPermissionRequest request) {
        rbacService.requireAdmin();
        CommunityRole role = rbacService.getRole(request.getId(), CommunityRbacConstants.DEFAULT_OID);
        PermissionVO result = new PermissionVO();
        result.setRoot(Boolean.TRUE.equals(role.getSystemRole()));
        result.setReadonly(Boolean.TRUE.equals(role.getSystemRole()));
        result.setPermissions(rbacService.roleMenuPermissions(role.getId()));
        result.setPermissionOrigins(List.of());
        return result;
    }

    @Override
    public PermissionVO menuTargetPermission(MenuPermissionRequest request) {
        unsupportedBusinessPermission();
        return null;
    }

    @Override
    public void saveBusiPer(BusiPerEditor editor) {
        unsupportedBusinessPermission();
    }

    @Override
    public void saveBusiTargetPer(BusiTargetPerCreator creator) {
        unsupportedBusinessPermission();
    }

    @Override
    public void saveMenuPer(MenuPerEditor editor) {
        rbacService.saveRoleMenuPermissions(editor.getId(), editor.getPermissions());
    }

    @Override
    public void saveMenuTargetPer(MenuTargetPerCreator creator) {
        unsupportedBusinessPermission();
    }

    @Override
    public List<ResourceItemVO> busiTargetPermissionAll(BusiPermissionRequest request) {
        unsupportedBusinessPermission();
        return List.of();
    }

    private void unsupportedBusinessPermission() {
        rbacService.requireAdmin();
        DEException.throwException("最小社区权限方案暂不支持对象级资源授权");
    }
}
