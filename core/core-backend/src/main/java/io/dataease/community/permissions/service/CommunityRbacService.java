package io.dataease.community.permissions.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.dataease.api.permissions.auth.vo.PermissionItem;
import io.dataease.api.permissions.role.dto.RoleCreator;
import io.dataease.api.permissions.role.dto.RoleEditor;
import io.dataease.api.permissions.user.dto.EnableSwitchRequest;
import io.dataease.api.permissions.user.dto.UserCreator;
import io.dataease.api.permissions.user.dto.UserEditor;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.auth.config.SubstituleLoginConfig;
import io.dataease.community.permissions.CommunityRbacConstants;
import io.dataease.community.permissions.dao.entity.CommunityRole;
import io.dataease.community.permissions.dao.entity.CommunityRoleMenu;
import io.dataease.community.permissions.dao.entity.CommunityUser;
import io.dataease.community.permissions.dao.entity.CommunityUserRole;
import io.dataease.community.permissions.dao.mapper.CommunityRoleMapper;
import io.dataease.community.permissions.dao.mapper.CommunityRoleMenuMapper;
import io.dataease.community.permissions.dao.mapper.CommunityUserMapper;
import io.dataease.community.permissions.dao.mapper.CommunityUserRoleMapper;
import io.dataease.community.permissions.security.CommunityForbiddenException;
import io.dataease.constant.AuthConstant;
import io.dataease.exception.DEException;
import io.dataease.menu.dao.auto.entity.CoreMenu;
import io.dataease.menu.dao.auto.mapper.CoreMenuMapper;
import io.dataease.result.ResultCode;
import io.dataease.utils.AuthUtils;
import io.dataease.utils.ServletUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.dataease.community.permissions.CommunityRbacConstants.ADMIN_ROLE_NAME;
import static io.dataease.community.permissions.CommunityRbacConstants.DEFAULT_OID;
import static io.dataease.community.permissions.CommunityRbacConstants.MANAGE_WEIGHT;
import static io.dataease.community.permissions.CommunityRbacConstants.READ_WEIGHT;

@Service
@ConditionalOnMissingBean(name = "loginServer")
@ConditionalOnProperty(name = "dataease.community-rbac.enabled", havingValue = "true")
public class CommunityRbacService {

    private static final int BCRYPT_COST = 12;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$");

    private final CommunityUserMapper userMapper;
    private final CommunityRoleMapper roleMapper;
    private final CommunityUserRoleMapper userRoleMapper;
    private final CommunityRoleMenuMapper roleMenuMapper;
    private final CoreMenuMapper menuMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CommunityRbacService(CommunityUserMapper userMapper,
                                CommunityRoleMapper roleMapper,
                                CommunityUserRoleMapper userRoleMapper,
                                CommunityRoleMenuMapper roleMenuMapper,
                                CoreMenuMapper menuMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    @Transactional
    public void bootstrap() {
        CommunityRole adminRole = roleMapper.selectOne(Wrappers.<CommunityRole>lambdaQuery()
                .eq(CommunityRole::getOid, DEFAULT_OID)
                .eq(CommunityRole::getSystemRole, true)
                .last("LIMIT 1"));
        if (adminRole == null) {
            adminRole = new CommunityRole();
            adminRole.setName(ADMIN_ROLE_NAME);
            adminRole.setDescription("内置系统管理员角色");
            adminRole.setSystemRole(true);
            adminRole.setOid(DEFAULT_OID);
            adminRole.setCreateTime(System.currentTimeMillis());
            roleMapper.insert(adminRole);
        }

        CommunityUser admin = findByAccount("admin");
        if (admin == null) {
            admin = new CommunityUser();
            admin.setId(1L);
            admin.setAccount("admin");
            admin.setPasswordHash(hashPassword(SubstituleLoginConfig.getPwd()));
            admin.setName("管理员");
            admin.setEnabled(true);
            admin.setDefaultOid(DEFAULT_OID);
            admin.setAuthVersion(1L);
            admin.setCreateTime(System.currentTimeMillis());
            userMapper.insert(admin);
        }
        if (userRoleMapper.selectCount(Wrappers.<CommunityUserRole>lambdaQuery()
                .eq(CommunityUserRole::getUserId, admin.getId())
                .eq(CommunityUserRole::getRoleId, adminRole.getId())
                .eq(CommunityUserRole::getOid, DEFAULT_OID)) == 0) {
            insertUserRole(admin.getId(), adminRole.getId(), DEFAULT_OID);
            bumpAuthVersions(List.of(admin.getId()));
        }
    }

    public CommunityUser authenticate(String account, String password) {
        CommunityUser user = findByAccount(account);
        if (user == null) {
            DEException.throwException(ResultCode.USER_LOGIN_ERROR.code(), "用户名或密码错误");
        }
        if (StringUtils.isBlank(password)
                || !OpenBSDBCrypt.checkPassword(user.getPasswordHash(), password.toCharArray())) {
            DEException.throwException(ResultCode.USER_LOGIN_ERROR.code(), "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            DEException.throwException(ResultCode.USER_ACCOUNT_FORBIDDEN.code(), "用户已被禁用");
        }
        return user;
    }

    public CommunityUser findByAccount(String account) {
        if (StringUtils.isBlank(account)) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<CommunityUser>lambdaQuery()
                .eq(CommunityUser::getAccount, account.trim())
                .last("LIMIT 1"));
    }

    public CommunityUser getUser(Long id) {
        CommunityUser user = userMapper.selectById(id);
        if (user == null) {
            DEException.throwException(ResultCode.USER_NOT_EXIST.code(), "用户不存在");
        }
        return user;
    }

    public CommunityUser currentUser() {
        TokenUserBO tokenUser = AuthUtils.getUser();
        if (tokenUser == null || tokenUser.getUserId() == null) {
            DEException.throwException(ResultCode.USER_NOT_LOGGED_IN.code(), "用户未登录");
        }
        return getUser(tokenUser.getUserId());
    }

    public void validateSession(TokenUserBO tokenUser, Long tokenAuthVersion) {
        CommunityUser user = userMapper.selectById(tokenUser.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || tokenAuthVersion == null
                || !Objects.equals(user.getAuthVersion(), tokenAuthVersion)
                || !Objects.equals(user.getDefaultOid(), tokenUser.getDefaultOid())) {
            DEException.throwException(ResultCode.USER_NOT_LOGGED_IN.code(), "登录状态或权限已变更，请重新登录");
        }
        tokenUser.setSysAdmin(isAdmin(user.getId(), user.getDefaultOid()));
    }

    public void requireAdmin() {
        if (StringUtils.isNotBlank(ServletUtils.getHead(AuthConstant.LINK_TOKEN_KEY)) || !isCurrentAdmin()) {
            throw new CommunityForbiddenException("当前操作仅系统管理员可执行");
        }
    }

    public boolean isCurrentAdmin() {
        TokenUserBO user = AuthUtils.getUser();
        return user != null && user.getUserId() != null && isAdmin(user.getUserId(), user.getDefaultOid());
    }

    public boolean isAdmin(Long userId, Long oid) {
        if (userId == null || oid == null) {
            return false;
        }
        List<Long> roleIds = roleIds(userId, oid);
        if (roleIds.isEmpty()) {
            return false;
        }
        return roleMapper.selectCount(Wrappers.<CommunityRole>lambdaQuery()
                .in(CommunityRole::getId, roleIds)
                .eq(CommunityRole::getOid, oid)
                .eq(CommunityRole::getSystemRole, true)) > 0;
    }

    public boolean hasMenuPermission(Long userId, Long oid, Long menuId, int requiredWeight) {
        if (isAdmin(userId, oid)) {
            return true;
        }
        List<Long> roleIds = roleIds(userId, oid);
        if (roleIds.isEmpty()) {
            return false;
        }
        return roleMenuMapper.selectCount(Wrappers.<CommunityRoleMenu>lambdaQuery()
                .in(CommunityRoleMenu::getRoleId, roleIds)
                .eq(CommunityRoleMenu::getMenuId, menuId)
                .ge(CommunityRoleMenu::getWeight, requiredWeight)) > 0;
    }

    public Set<Long> authorizedMenuIds(Long userId, Long oid) {
        if (isAdmin(userId, oid)) {
            return menuMapper.selectList(null).stream().map(CoreMenu::getId).collect(Collectors.toSet());
        }
        List<Long> roleIds = roleIds(userId, oid);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return roleMenuMapper.selectList(Wrappers.<CommunityRoleMenu>lambdaQuery()
                        .in(CommunityRoleMenu::getRoleId, roleIds)
                        .ge(CommunityRoleMenu::getWeight, READ_WEIGHT))
                .stream().map(CommunityRoleMenu::getMenuId).collect(Collectors.toSet());
    }

    public List<CommunityRole> listRoles(String keyword, Long oid) {
        var wrapper = Wrappers.<CommunityRole>lambdaQuery().eq(CommunityRole::getOid, oid)
                .orderByDesc(CommunityRole::getSystemRole).orderByAsc(CommunityRole::getName);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(CommunityRole::getName, keyword.trim());
        }
        return roleMapper.selectList(wrapper);
    }

    public CommunityRole getRole(Long id, Long oid) {
        CommunityRole role = roleMapper.selectOne(Wrappers.<CommunityRole>lambdaQuery()
                .eq(CommunityRole::getId, id).eq(CommunityRole::getOid, oid));
        if (role == null) {
            DEException.throwException("角色不存在或不在当前访问范围");
        }
        return role;
    }

    @Transactional
    public Long createRole(RoleCreator creator) {
        requireAdmin();
        String name = requiredText(creator.getName(), "角色名称");
        ensureUniqueRoleName(name, null, DEFAULT_OID);
        CommunityRole role = new CommunityRole();
        role.setName(name);
        role.setDescription(StringUtils.trimToNull(creator.getDesc()));
        role.setSystemRole(false);
        role.setOid(DEFAULT_OID);
        role.setCreateTime(System.currentTimeMillis());
        roleMapper.insert(role);
        return role.getId();
    }

    @Transactional
    public void editRole(RoleEditor editor) {
        requireAdmin();
        CommunityRole role = getRole(editor.getId(), DEFAULT_OID);
        if (Boolean.TRUE.equals(role.getSystemRole())) {
            throw new CommunityForbiddenException("内置系统管理员角色不可编辑");
        }
        String name = requiredText(editor.getName(), "角色名称");
        ensureUniqueRoleName(name, role.getId(), role.getOid());
        role.setName(name);
        role.setDescription(StringUtils.trimToNull(editor.getDesc()));
        roleMapper.updateById(role);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        requireAdmin();
        CommunityRole role = getRole(roleId, DEFAULT_OID);
        if (Boolean.TRUE.equals(role.getSystemRole())) {
            throw new CommunityForbiddenException("内置系统管理员角色不可删除");
        }
        List<Long> affectedUsers = userRoleMapper.selectList(Wrappers.<CommunityUserRole>lambdaQuery()
                        .eq(CommunityUserRole::getRoleId, roleId)
                        .eq(CommunityUserRole::getOid, role.getOid()))
                .stream().map(CommunityUserRole::getUserId).distinct().toList();
        roleMenuMapper.delete(Wrappers.<CommunityRoleMenu>lambdaQuery().eq(CommunityRoleMenu::getRoleId, roleId));
        userRoleMapper.delete(Wrappers.<CommunityUserRole>lambdaQuery().eq(CommunityUserRole::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        bumpAuthVersions(affectedUsers);
    }

    @Transactional
    public void copyRole(Long sourceRoleId, String name) {
        requireAdmin();
        CommunityRole source = getRole(sourceRoleId, DEFAULT_OID);
        RoleCreator creator = new RoleCreator();
        creator.setName(name);
        creator.setDesc(source.getDescription());
        Long targetId = createRole(creator);
        List<CommunityRoleMenu> sourcePermissions = roleMenuMapper.selectList(Wrappers.<CommunityRoleMenu>lambdaQuery()
                .eq(CommunityRoleMenu::getRoleId, sourceRoleId));
        for (CommunityRoleMenu sourcePermission : sourcePermissions) {
            CommunityRoleMenu target = new CommunityRoleMenu();
            target.setRoleId(targetId);
            target.setMenuId(sourcePermission.getMenuId());
            target.setWeight(sourcePermission.getWeight());
            roleMenuMapper.insert(target);
        }
    }

    public List<CommunityUser> listUsers(String keyword, Long oid) {
        var wrapper = Wrappers.<CommunityUser>lambdaQuery().eq(CommunityUser::getDefaultOid, oid)
                .orderByAsc(CommunityUser::getAccount);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(query -> query.like(CommunityUser::getAccount, keyword.trim())
                    .or().like(CommunityUser::getName, keyword.trim()));
        }
        return userMapper.selectList(wrapper);
    }

    @Transactional
    public Long createUser(UserCreator creator, String password) {
        requireAdmin();
        String account = requiredText(creator.getAccount(), "账号");
        if (findByAccount(account) != null) {
            DEException.throwException(ResultCode.USER_HAS_EXISTED.code(), "账号已存在");
        }
        validatePassword(password);
        CommunityUser user = new CommunityUser();
        user.setAccount(account);
        user.setPasswordHash(hashPassword(password));
        user.setName(requiredText(creator.getName(), "用户名称"));
        user.setEmail(StringUtils.trimToNull(creator.getEmail()));
        user.setPhonePrefix(StringUtils.trimToNull(creator.getPhonePrefix()));
        user.setPhone(StringUtils.trimToNull(creator.getPhone()));
        user.setEnabled(!Boolean.FALSE.equals(creator.getEnable()));
        user.setDefaultOid(DEFAULT_OID);
        user.setAuthVersion(1L);
        user.setCreateTime(System.currentTimeMillis());
        userMapper.insert(user);
        replaceUserRoles(user.getId(), creator.getRoleIds(), DEFAULT_OID);
        return user.getId();
    }

    @Transactional
    public void editUser(UserEditor editor, String password) {
        requireAdmin();
        CommunityUser user = getUserInScope(editor.getId(), DEFAULT_OID);
        List<Long> newRoleIds = normalizeRoleIds(editor.getRoleIds(), DEFAULT_OID);
        if (isAdmin(user.getId(), DEFAULT_OID) && !containsSystemRole(newRoleIds, DEFAULT_OID)) {
            assertNotLastAdmin(user.getId(), DEFAULT_OID);
        }
        String account = requiredText(editor.getAccount(), "账号");
        CommunityUser accountOwner = findByAccount(account);
        if (accountOwner != null && !accountOwner.getId().equals(user.getId())) {
            DEException.throwException(ResultCode.USER_HAS_EXISTED.code(), "账号已存在");
        }
        user.setAccount(account);
        user.setName(requiredText(editor.getName(), "用户名称"));
        user.setEmail(StringUtils.trimToNull(editor.getEmail()));
        user.setPhonePrefix(StringUtils.trimToNull(editor.getPhonePrefix()));
        user.setPhone(StringUtils.trimToNull(editor.getPhone()));
        if (editor.getEnable() != null && !editor.getEnable() && Boolean.TRUE.equals(user.getEnabled()) && isAdmin(user.getId(), DEFAULT_OID)) {
            assertNotLastAdmin(user.getId(), DEFAULT_OID);
        }
        if (editor.getEnable() != null) {
            user.setEnabled(editor.getEnable());
        }
        if (StringUtils.isNotBlank(password)) {
            validatePassword(password);
            user.setPasswordHash(hashPassword(password));
        }
        userMapper.updateById(user);
        replaceUserRoles(user.getId(), newRoleIds, DEFAULT_OID);
        bumpAuthVersions(List.of(user.getId()));
    }

    @Transactional
    public void switchUserEnabled(EnableSwitchRequest request) {
        requireAdmin();
        if (request.getEnable() == null) {
            DEException.throwException("用户状态不能为空");
        }
        CommunityUser user = getUserInScope(request.getId(), DEFAULT_OID);
        if (Boolean.FALSE.equals(request.getEnable()) && Boolean.TRUE.equals(user.getEnabled()) && isAdmin(user.getId(), DEFAULT_OID)) {
            assertNotLastAdmin(user.getId(), DEFAULT_OID);
        }
        user.setEnabled(Boolean.TRUE.equals(request.getEnable()));
        userMapper.updateById(user);
        bumpAuthVersions(List.of(user.getId()));
    }

    @Transactional
    public void deleteUser(Long userId) {
        requireAdmin();
        CommunityUser user = getUserInScope(userId, DEFAULT_OID);
        if (isAdmin(userId, DEFAULT_OID)) {
            assertNotLastAdmin(userId, DEFAULT_OID);
        }
        userRoleMapper.delete(Wrappers.<CommunityUserRole>lambdaQuery().eq(CommunityUserRole::getUserId, userId));
        userMapper.deleteById(user.getId());
    }

    @Transactional
    public void changeOwnPassword(String oldPassword, String newPassword) {
        CommunityUser user = currentUser();
        if (!OpenBSDBCrypt.checkPassword(user.getPasswordHash(), oldPassword.toCharArray())) {
            DEException.throwException("原密码错误");
        }
        validatePassword(newPassword);
        if (oldPassword.equals(newPassword)) {
            DEException.throwException("新旧密码不能相同");
        }
        user.setPasswordHash(hashPassword(newPassword));
        userMapper.updateById(user);
        bumpAuthVersions(List.of(user.getId()));
    }

    @Transactional
    public void mountUsers(Long roleId, Collection<Long> userIds) {
        requireAdmin();
        CommunityRole role = getRole(roleId, DEFAULT_OID);
        if (userIds == null) {
            return;
        }
        Set<Long> affected = new HashSet<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            getUserInScope(userId, role.getOid());
            if (userRoleMapper.selectCount(Wrappers.<CommunityUserRole>lambdaQuery()
                    .eq(CommunityUserRole::getUserId, userId)
                    .eq(CommunityUserRole::getRoleId, roleId)
                    .eq(CommunityUserRole::getOid, role.getOid())) == 0) {
                insertUserRole(userId, roleId, role.getOid());
                affected.add(userId);
            }
        }
        bumpAuthVersions(affected);
    }

    @Transactional
    public void unmountUser(Long roleId, Long userId) {
        requireAdmin();
        CommunityRole role = getRole(roleId, DEFAULT_OID);
        getUserInScope(userId, role.getOid());
        if (Boolean.TRUE.equals(role.getSystemRole())) {
            assertNotLastAdmin(userId, role.getOid());
        }
        userRoleMapper.delete(Wrappers.<CommunityUserRole>lambdaQuery()
                .eq(CommunityUserRole::getUserId, userId)
                .eq(CommunityUserRole::getRoleId, roleId)
                .eq(CommunityUserRole::getOid, role.getOid()));
        bumpAuthVersions(List.of(userId));
    }

    public List<Long> roleIds(Long userId, Long oid) {
        return userRoleMapper.selectList(Wrappers.<CommunityUserRole>lambdaQuery()
                        .eq(CommunityUserRole::getUserId, userId)
                        .eq(CommunityUserRole::getOid, oid))
                .stream().map(CommunityUserRole::getRoleId).distinct().toList();
    }

    public List<Long> userIdsForRole(Long roleId, Long oid) {
        getRole(roleId, oid);
        return userRoleMapper.selectList(Wrappers.<CommunityUserRole>lambdaQuery()
                        .eq(CommunityUserRole::getRoleId, roleId)
                        .eq(CommunityUserRole::getOid, oid))
                .stream().map(CommunityUserRole::getUserId).distinct().toList();
    }

    public Map<Long, CommunityRole> rolesById(Long oid) {
        return listRoles(null, oid).stream().collect(Collectors.toMap(CommunityRole::getId, Function.identity()));
    }

    public List<PermissionItem> roleMenuPermissions(Long roleId) {
        getRole(roleId, DEFAULT_OID);
        return roleMenuMapper.selectList(Wrappers.<CommunityRoleMenu>lambdaQuery()
                        .eq(CommunityRoleMenu::getRoleId, roleId)
                        .ne(CommunityRoleMenu::getMenuId, 3L))
                .stream().map(permission -> {
                    PermissionItem item = new PermissionItem();
                    item.setId(permission.getMenuId());
                    item.setWeight(permission.getWeight());
                    return item;
                }).toList();
    }

    @Transactional
    public void saveRoleMenuPermissions(Long roleId, List<PermissionItem> permissions) {
        requireAdmin();
        CommunityRole role = getRole(roleId, DEFAULT_OID);
        if (Boolean.TRUE.equals(role.getSystemRole())) {
            throw new CommunityForbiddenException("内置系统管理员角色始终拥有全部权限");
        }
        Set<Long> existingMenuIds = menuMapper.selectList(null).stream().map(CoreMenu::getId).collect(Collectors.toSet());
        List<PermissionItem> safePermissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
        PermissionItem visualizationPermission = safePermissions.stream()
                .filter(item -> Objects.equals(item.getId(), 2L) || Objects.equals(item.getId(), 3L))
                .max(java.util.Comparator.comparingInt(PermissionItem::getWeight)).orElse(null);
        if (visualizationPermission != null) {
            safePermissions.removeIf(item -> Objects.equals(item.getId(), 2L) || Objects.equals(item.getId(), 3L));
            for (long menuId : List.of(2L, 3L)) {
                PermissionItem item = new PermissionItem();
                item.setId(menuId);
                item.setWeight(visualizationPermission.getWeight());
                safePermissions.add(item);
            }
        }
        for (PermissionItem permission : safePermissions) {
            if (permission.getId() == null || !existingMenuIds.contains(permission.getId())
                    || (permission.getWeight() != READ_WEIGHT && permission.getWeight() != MANAGE_WEIGHT)) {
                DEException.throwException("无效的菜单权限");
            }
        }
        roleMenuMapper.delete(Wrappers.<CommunityRoleMenu>lambdaQuery().eq(CommunityRoleMenu::getRoleId, roleId));
        for (PermissionItem permission : safePermissions.stream()
                .collect(Collectors.toMap(PermissionItem::getId, Function.identity(), (left, right) -> right))
                .values()) {
            CommunityRoleMenu roleMenu = new CommunityRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(permission.getId());
            roleMenu.setWeight(permission.getWeight());
            roleMenuMapper.insert(roleMenu);
        }
        bumpAuthVersions(userIdsForRole(roleId, role.getOid()));
    }

    public List<CoreMenu> authorizableMenus() {
        return menuMapper.selectList(Wrappers.<CoreMenu>lambdaQuery()
                .eq(CoreMenu::getAuth, true)
                .notIn(CoreMenu::getId, 3L, CommunityRbacConstants.USER_MENU_ID, CommunityRbacConstants.ROLE_MENU_ID)
                .orderByAsc(CoreMenu::getMenuSort));
    }

    private CommunityUser getUserInScope(Long userId, Long oid) {
        CommunityUser user = getUser(userId);
        if (!Objects.equals(user.getDefaultOid(), oid)) {
            throw new CommunityForbiddenException("用户不在当前访问范围");
        }
        return user;
    }

    private void ensureUniqueRoleName(String name, Long excludedId, Long oid) {
        var wrapper = Wrappers.<CommunityRole>lambdaQuery().eq(CommunityRole::getOid, oid).eq(CommunityRole::getName, name);
        if (excludedId != null) {
            wrapper.ne(CommunityRole::getId, excludedId);
        }
        if (roleMapper.selectCount(wrapper) > 0) {
            DEException.throwException("角色名称已存在");
        }
    }

    private void replaceUserRoles(Long userId, Collection<Long> roleIds, Long oid) {
        List<Long> normalized = normalizeRoleIds(roleIds, oid);
        userRoleMapper.delete(Wrappers.<CommunityUserRole>lambdaQuery()
                .eq(CommunityUserRole::getUserId, userId).eq(CommunityUserRole::getOid, oid));
        normalized.forEach(roleId -> insertUserRole(userId, roleId, oid));
    }

    private List<Long> normalizeRoleIds(Collection<Long> roleIds, Long oid) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>(new LinkedHashSet<>(roleIds));
        long count = roleMapper.selectCount(Wrappers.<CommunityRole>lambdaQuery()
                .in(CommunityRole::getId, normalized).eq(CommunityRole::getOid, oid));
        if (count != normalized.size()) {
            throw new CommunityForbiddenException("角色不存在或不在当前访问范围");
        }
        return normalized;
    }

    private boolean containsSystemRole(Collection<Long> roleIds, Long oid) {
        return roleIds != null && !roleIds.isEmpty() && roleMapper.selectCount(Wrappers.<CommunityRole>lambdaQuery()
                .in(CommunityRole::getId, roleIds).eq(CommunityRole::getOid, oid)
                .eq(CommunityRole::getSystemRole, true)) > 0;
    }

    private void insertUserRole(Long userId, Long roleId, Long oid) {
        CommunityUserRole relation = new CommunityUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setOid(oid);
        userRoleMapper.insert(relation);
    }

    private void assertNotLastAdmin(Long userId, Long oid) {
        List<Long> enabledAdminIds = userMapper.lockEnabledAdminIds(oid);
        if (enabledAdminIds.contains(userId) && enabledAdminIds.size() <= 1) {
            throw new CommunityForbiddenException("必须保留至少一个可登录的系统管理员");
        }
    }

    private void bumpAuthVersions(Collection<Long> userIds) {
        if (userIds == null) {
            return;
        }
        for (Long userId : new LinkedHashSet<>(userIds)) {
            CommunityUser user = userMapper.selectById(userId);
            if (user != null) {
                long currentVersion = user.getAuthVersion() == null ? 0L : user.getAuthVersion();
                user.setAuthVersion(currentVersion + 1);
                userMapper.updateById(user);
            }
        }
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return OpenBSDBCrypt.generate(password.toCharArray(), salt, BCRYPT_COST);
    }

    private void validatePassword(String password) {
        if (StringUtils.isBlank(password) || !PASSWORD_PATTERN.matcher(password).matches()) {
            DEException.throwException("密码必须为8-64位，并包含大写字母、小写字母、数字和特殊字符");
        }
    }

    private String requiredText(String value, String fieldName) {
        String result = StringUtils.trimToNull(value);
        if (result == null || result.length() > 128) {
            DEException.throwException(fieldName + "不能为空且不能超过128个字符");
        }
        return result;
    }
}
