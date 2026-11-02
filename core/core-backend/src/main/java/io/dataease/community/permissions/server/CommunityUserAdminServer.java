package io.dataease.community.permissions.server;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.dataease.api.permissions.role.dto.UserRequest;
import io.dataease.api.permissions.user.dto.EnableSwitchRequest;
import io.dataease.api.permissions.user.dto.UserCreator;
import io.dataease.api.permissions.user.dto.UserEditor;
import io.dataease.api.permissions.user.vo.UserFormVO;
import io.dataease.api.permissions.user.vo.UserGridRoleItem;
import io.dataease.api.permissions.user.vo.UserGridVO;
import io.dataease.api.permissions.user.vo.UserItemVO;
import io.dataease.community.permissions.CommunityRbacConstants;
import io.dataease.community.permissions.dao.entity.CommunityRole;
import io.dataease.community.permissions.dao.entity.CommunityUser;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.utils.RsaUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/user")
@ConditionalOnMissingBean(name = "userServer")
@ConditionalOnProperty(name = "dataease.community-rbac.enabled", havingValue = "true")
public class CommunityUserAdminServer {

    private final CommunityRbacService rbacService;

    public CommunityUserAdminServer(CommunityRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @PostMapping("/pager/{goPage}/{pageSize}")
    public Page<UserGridVO> pager(@PathVariable int goPage, @PathVariable int pageSize,
                                  @RequestBody(required = false) UserRequest request) {
        rbacService.requireAdmin();
        String keyword = request == null ? null : request.getKeyword();
        List<CommunityUser> users = rbacService.listUsers(keyword, CommunityRbacConstants.DEFAULT_OID);
        int safePage = Math.max(goPage, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        int from = Math.min((safePage - 1) * safeSize, users.size());
        int to = Math.min(from + safeSize, users.size());
        Page<UserGridVO> result = new Page<>(safePage, safeSize, users.size());
        result.setRecords(users.subList(from, to).stream().map(this::toGrid).toList());
        return result;
    }

    @GetMapping("/queryById/{id}")
    public UserFormVO queryById(@PathVariable Long id) {
        rbacService.requireAdmin();
        CommunityUser user = rbacService.getUser(id);
        if (CommunityRbacConstants.DEFAULT_OID != user.getDefaultOid()) {
            throw new io.dataease.community.permissions.security.CommunityForbiddenException("用户不在当前访问范围");
        }
        return toForm(user);
    }

    @PostMapping("/create")
    public Long create(@RequestBody UserCreator creator) {
        return rbacService.createUser(creator, decryptPassword(creator.getPassword(), true));
    }

    @PostMapping("/edit")
    public void edit(@RequestBody UserEditor editor) {
        rbacService.editUser(editor, decryptPassword(editor.getPassword(), false));
    }

    @PostMapping("/delete/{id}")
    public void delete(@PathVariable Long id) {
        rbacService.deleteUser(id);
    }

    @PostMapping("/batchDel")
    public void batchDelete(@RequestBody List<Long> ids) {
        rbacService.requireAdmin();
        for (Long id : new ArrayList<>(new HashSet<>(ids))) {
            rbacService.deleteUser(id);
        }
    }

    @PostMapping("/enable")
    public void enable(@RequestBody EnableSwitchRequest request) {
        rbacService.switchUserEnabled(request);
    }

    @PostMapping("/role/option")
    public List<UserItemVO> optionForRole(@RequestBody UserRequest request) {
        rbacService.requireAdmin();
        Set<Long> selected = new HashSet<>(rbacService.userIdsForRole(request.getRid(), CommunityRbacConstants.DEFAULT_OID));
        return rbacService.listUsers(request.getKeyword(), CommunityRbacConstants.DEFAULT_OID).stream()
                .filter(user -> !selected.contains(user.getId())).map(this::toItem).toList();
    }

    @PostMapping("/role/selected/{goPage}/{pageSize}")
    public Page<UserItemVO> selectedForRole(@PathVariable int goPage, @PathVariable int pageSize,
                                            @RequestBody UserRequest request) {
        rbacService.requireAdmin();
        Set<Long> selected = new HashSet<>(rbacService.userIdsForRole(request.getRid(), CommunityRbacConstants.DEFAULT_OID));
        List<UserItemVO> users = rbacService.listUsers(request.getKeyword(), CommunityRbacConstants.DEFAULT_OID).stream()
                .filter(user -> selected.contains(user.getId())).map(this::toItem).toList();
        int safePage = Math.max(goPage, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        int from = Math.min((safePage - 1) * safeSize, users.size());
        int to = Math.min(from + safeSize, users.size());
        Page<UserItemVO> result = new Page<>(safePage, safeSize, users.size());
        result.setRecords(users.subList(from, to));
        return result;
    }

    private UserGridVO toGrid(CommunityUser user) {
        UserGridVO result = new UserGridVO();
        result.setId(user.getId());
        result.setAccount(user.getAccount());
        result.setName(user.getName());
        result.setEmail(user.getEmail());
        result.setPhonePrefix(user.getPhonePrefix());
        result.setPhone(user.getPhone());
        result.setEnable(user.getEnabled());
        result.setCreateTime(user.getCreateTime());
        result.setOrigin(0);
        Map<Long, CommunityRole> roles = rbacService.rolesById(user.getDefaultOid());
        List<UserGridRoleItem> roleItems = rbacService.roleIds(user.getId(), user.getDefaultOid()).stream()
                .filter(roles::containsKey).map(roleId -> {
                    UserGridRoleItem item = new UserGridRoleItem();
                    item.setId(roleId);
                    item.setName(roles.get(roleId).getName());
                    return item;
                }).toList();
        result.setRoleItems(roleItems);
        return result;
    }

    private UserFormVO toForm(CommunityUser user) {
        UserFormVO result = new UserFormVO();
        result.setId(user.getId());
        result.setAccount(user.getAccount());
        result.setName(user.getName());
        result.setEmail(user.getEmail());
        result.setPhonePrefix(user.getPhonePrefix());
        result.setPhone(user.getPhone());
        result.setEnable(user.getEnabled());
        result.setRoleIds(rbacService.roleIds(user.getId(), user.getDefaultOid()).stream().map(String::valueOf).toList());
        result.setModel("lose");
        return result;
    }

    private UserItemVO toItem(CommunityUser user) {
        UserItemVO result = new UserItemVO();
        result.setId(user.getId());
        result.setAccount(user.getAccount());
        result.setName(user.getName());
        result.setEmail(user.getEmail());
        return result;
    }

    private String decryptPassword(String encryptedPassword, boolean required) {
        if (StringUtils.isBlank(encryptedPassword)) {
            if (required) {
                io.dataease.exception.DEException.throwException("密码不能为空");
            }
            return null;
        }
        return RsaUtils.decryptStr(encryptedPassword);
    }
}
