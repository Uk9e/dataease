package io.dataease.community.permissions.server;

import io.dataease.api.permissions.role.api.RoleApi;
import io.dataease.api.permissions.role.dto.MountExternalUserRequest;
import io.dataease.api.permissions.role.dto.MountUserRequest;
import io.dataease.api.permissions.role.dto.RoleCopyRequest;
import io.dataease.api.permissions.role.dto.RoleCreator;
import io.dataease.api.permissions.role.dto.RoleEditor;
import io.dataease.api.permissions.role.dto.RoleRequest;
import io.dataease.api.permissions.role.dto.UnmountUserRequest;
import io.dataease.api.permissions.role.vo.ExternalUserVO;
import io.dataease.api.permissions.role.vo.RoleDetailVO;
import io.dataease.api.permissions.role.vo.RoleVO;
import io.dataease.community.permissions.CommunityRbacConstants;
import io.dataease.community.permissions.dao.entity.CommunityRole;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.exception.DEException;
import io.dataease.model.KeywordRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController("communityRoleServer")
@RequestMapping("/role")
@ConditionalOnMissingBean(name = "roleServer")
@ConditionalOnProperty(name = "dataease.community-rbac.enabled", havingValue = "true")
public class CommunityRoleServer implements RoleApi {

    private final CommunityRbacService rbacService;

    public CommunityRoleServer(CommunityRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public List<RoleVO> query(KeywordRequest request) {
        rbacService.requireAdmin();
        return roles(request == null ? null : request.getKeyword());
    }

    @Override
    public Long create(RoleCreator creator) {
        return rbacService.createRole(creator);
    }

    @Override
    public void edit(RoleEditor editor) {
        rbacService.editRole(editor);
    }

    @Override
    public void mountUser(MountUserRequest request) {
        rbacService.mountUsers(request.getRid(), request.getUids());
    }

    @Override
    public void mountExternalUser(MountExternalUserRequest request) {
        DEException.throwException("社区角色不支持跨组织用户");
    }

    @Override
    public ExternalUserVO searchExternalUser(String keyword) {
        rbacService.requireAdmin();
        return new ExternalUserVO();
    }

    @Override
    public void unMountUser(UnmountUserRequest request) {
        rbacService.unmountUser(request.getRid(), request.getUid());
    }

    @Override
    public List<RoleVO> optionForUser(RoleRequest request) {
        rbacService.requireAdmin();
        Set<Long> selected = Set.copyOf(rbacService.roleIds(request.getUid(), CommunityRbacConstants.DEFAULT_OID));
        return roles(request.getKeyword()).stream().filter(role -> !selected.contains(role.getId())).toList();
    }

    @Override
    public List<RoleVO> selectedForUser(RoleRequest request) {
        rbacService.requireAdmin();
        Set<Long> selected = Set.copyOf(rbacService.roleIds(request.getUid(), CommunityRbacConstants.DEFAULT_OID));
        return roles(request.getKeyword()).stream().filter(role -> selected.contains(role.getId())).toList();
    }

    @Override
    public RoleDetailVO detail(Long rid) {
        rbacService.requireAdmin();
        CommunityRole role = rbacService.getRole(rid, CommunityRbacConstants.DEFAULT_OID);
        RoleDetailVO result = new RoleDetailVO();
        result.setId(role.getId());
        result.setRid(role.getId());
        result.setName(role.getName());
        result.setDesc(role.getDescription());
        result.setTypeCode(Boolean.TRUE.equals(role.getSystemRole()) ? 1 : 0);
        return result;
    }

    @Override
    public void delete(Long rid) {
        rbacService.deleteRole(rid);
    }

    @Override
    public Integer beforeUnmountInfo(UnmountUserRequest request) {
        rbacService.requireAdmin();
        return 0;
    }

    @Override
    public void copy(RoleCopyRequest request) {
        rbacService.copyRole(request.getCopyId(), request.getName());
    }

    @Override
    public List<RoleVO> byCurOrg(KeywordRequest request) {
        rbacService.requireAdmin();
        return roles(request == null ? null : request.getKeyword());
    }

    @Override
    public List<RoleVO> queryWithOid(Long oid) {
        rbacService.requireAdmin();
        if (CommunityRbacConstants.DEFAULT_OID != oid) {
            throw new io.dataease.community.permissions.security.CommunityForbiddenException("组织不在当前访问范围");
        }
        return roles(null);
    }

    private List<RoleVO> roles(String keyword) {
        return rbacService.listRoles(keyword, CommunityRbacConstants.DEFAULT_OID).stream().map(role -> {
            RoleVO result = new RoleVO();
            result.setId(role.getId());
            result.setName(role.getName());
            result.setReadonly(Boolean.TRUE.equals(role.getSystemRole()));
            result.setRoot(Boolean.TRUE.equals(role.getSystemRole()));
            return result;
        }).toList();
    }
}
