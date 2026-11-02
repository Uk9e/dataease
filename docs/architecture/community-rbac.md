# 社区版角色权限

## 适用范围

社区 RBAC 仅在 standalone 配置中的 `dataease.community-rbac.enabled=true` 时启用。desktop 仍使用固定本地管理员，distributed/X-Pack 不启用这套实现。

## 复用的项目契约

- 用户和角色接口沿用 `api-permissions` 中的 `UserApi`、`RoleApi`、`AuthApi` 请求与响应模型。
- 功能入口沿用 `core_menu` 和 `/menu/query` 动态路由。
- 后端功能权限沿用 `@DeApiPath`、`@DePermit`。社区实现把现有表达式归并为菜单“查看”和“管理”两级，不实现对象级、行列级权限。仪表板与数据大屏共用同一组可视化后端接口，因此 MVP 将两者作为一个授权项，同时授予两个菜单。
- 登录继续使用 `X-DE-TOKEN`、现有 HMAC secret 和 RSA 登录参数，不引入新的 Token 或加密协议。

## 数据模型

Flyway 迁移创建：

- `core_rbac_user`：社区用户、BCrypt 密码哈希、启用状态和 `auth_version`。
- `core_rbac_role`：角色；`system_role=1` 表示内置系统管理员角色。
- `core_rbac_user_role`：限定 `oid` 的用户角色关系。
- `core_rbac_role_menu`：角色菜单权限，`weight=1` 为查看，`weight=2` 为管理。

standalone 迁移同时在 `core_menu` 的系统设置节点下增加用户管理和角色管理菜单；desktop 迁移只预建兼容表，不注册菜单或启用多用户。启动初始化器在空库中创建系统管理员角色，把原 substitute admin 密码以 BCrypt 形式写入 `core_rbac_user`，并绑定管理员角色。

## 请求鉴权

1. `TokenFilter` 解析身份，`CommunityTokenFilter` 校验社区 JWT 签名和过期时间。
2. `CommunityRbacSessionFilter` 校验用户启用状态、组织及 token 中的 `authVersion`。
3. `CommunityPermissionInterceptor` 对实现了 `@DeApiPath` 契约的控制器检查菜单权限；`@DePermit` 中的 `manage/export` 需要管理权重，查询类操作需要查看权重。
4. 用户、角色、授权和系统参数写接口强制要求系统管理员。服务层对角色授权、成员绑定和用户变更再次校验管理员身份及 `oid`。
5. 分享 token 继续由 `LinkInterceptor` 和 `@DeLinkPermit` 限定资源，并且不能借用分享创建者的管理员身份调用管理接口。

权限、角色绑定、密码和用户启用状态变化会增加受影响用户的 `auth_version`。旧 token 在下一次请求返回 401，用户必须重新登录。

## 最后管理员保护

内置系统管理员角色不可编辑、删除或修改菜单权限。移除管理员角色、禁用或删除管理员用户前，服务会在事务内锁定当前组织的可登录管理员记录；操作后如果没有管理员则拒绝并回滚。

## 使用方式

1. 使用原有 admin 账号登录。首次启动会自动迁移 admin 密码。
2. 在“系统设置 > 角色管理”创建角色，选择菜单并设置查看或管理权限，然后绑定成员。
3. 在“系统设置 > 用户管理”创建用户、设置初始密码并分配角色。
4. 权限变更后，受影响用户的旧会话会失效；重新登录后获取新菜单和权限。

新密码必须为 8–64 位，并同时包含大写字母、小写字母、数字和特殊字符。

## 回滚

关闭 `dataease.community-rbac.enabled` 可停止注册社区 RBAC 服务，但启用过多用户后不应直接回到固定 admin 模式。Flyway 不自动删除新增表；数据库级回滚使用上线前备份。生产执行迁移和回滚仍需单独审批。
