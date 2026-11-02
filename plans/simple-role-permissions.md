# 简单角色权限功能实施计划

## 目标与范围

在 DataEase 社区版 standalone 模式中补齐最小 RBAC：数据库用户、角色、用户-角色绑定、菜单/业务操作权限、后端强制鉴权、权限撤销后的会话失效和最后管理员保护。

本期不实现行列数据权限、自定义策略、多级角色继承、SSO 或新的嵌入认证方案。distributed/X-Pack 已有实现保持优先，社区实现必须避免与其控制器和 Bean 冲突。

## 已验证现状

- 社区登录只接受 `admin`，用户固定为 `uid=1/oid=1`；当前工作区还存在一处用户未提交改动，注释掉了密码错误抛出逻辑。位置：`core/core-backend/src/main/java/io/dataease/substitute/permissions/login/SubstituleLoginServer.java`。
- 社区用户接口只返回固定管理员信息。位置：`core/core-backend/src/main/java/io/dataease/substitute/permissions/user/SubstituteUserServer.java`。
- SDK 已定义 `UserApi`、`RoleApi`、`AuthApi`、`@DeApiPath`、`@DePermit` 及相关 DTO/VO，前端也已有 `src/api/user.ts`、`src/api/auth.ts` 请求封装；当前开源源码没有角色/授权接口实现，也没有用户/角色管理页面。
- `MenuServer` 返回 `core_menu` 全量菜单；`MenuManage.query` 预留了 `@XpackInteract("menuApi")` 扩展点。位置：`core/core-backend/src/main/java/io/dataease/menu/`。
- 前端已经从 `/menu/query` 生成动态路由，并有 `v-permission` 和权限错误处理，可复用。位置：`core/core-frontend/src/permission.ts`、`src/store/modules/permission.ts`、`src/directive/Permission`、`src/config/axios/service.ts`。
- `TokenFilter` 负责白名单和用户上下文，`CommunityTokenFilter` 校验社区 JWT 签名；当前 token 不带过期时间，`TokenUtils` 先解码、后由社区过滤器验签。位置：`sdk/common/src/main/java/io/dataease/auth/filter/`、`sdk/common/src/main/java/io/dataease/utils/TokenUtils.java`。
- `@DePermit` 在开源源码中只有定义和使用，没有处理切面或拦截器，因此当前不会形成后端业务权限校验。
- 分享 token 由 `TokenFilter` 单独识别并建立用户上下文；嵌入初始化路径在白名单中，但社区源码没有嵌入权限实现。中央鉴权必须显式区分普通、分享和嵌入访问，避免分享 token 调用管理接口，也避免无意破坏现有分享查看流程。

## 需人工确认的边界

1. **数据库模式迁移**：需要新增社区 RBAC 持久化表及索引，并增加用户/角色菜单项。建议使用社区专用表名，避免碰撞闭源 X-Pack 的 `per_*` 表：
   - `core_rbac_user`
   - `core_rbac_role`
   - `core_rbac_user_role`
   - `core_rbac_role_menu`
   - 可选的 `core_rbac_role_action`，仅在现有菜单权重无法表达业务操作时增加
2. **权限语义变化**：社区版现有 `@DePermit` 从“不生效”改为“默认拒绝并强制执行”。`m:read` 表示可进入和调用该菜单下的普通功能，`m:manage` 表示管理操作；资源表达式仍沿用现有 `read/manage/export` 命名。本期优先覆盖用户、角色、授权和关键菜单接口，不宣称一次性覆盖所有历史业务接口。
3. **登录与会话变化**：启用后旧社区 token 因缺少会话版本声明而失效，所有用户需要重新登录。权限/角色变更会增加用户 `auth_version`，其旧 token 在下一次受保护请求时返回 401。
4. **分享与嵌入兼容**：分享 token 只允许现有分享查看所需接口，明确禁止 `/user`、`/role`、`/auth` 等管理接口；分享回归必须通过。嵌入功能不在本期补实现，但嵌入 token 同样不得调用管理接口。若中央鉴权发现现有分享请求依赖普通用户权限，将记录具体端点并停止扩大改动。
5. **现有用户改动冲突**：安全登录必须恢复或替换 `SubstituleLoginServer` 中当前被注释的密码校验。实施时只处理这一处与需求直接冲突的改动，不覆盖其他未提交文件。

## 最小实施步骤

### 后端与数据

1. 为 MySQL migration 和 desktop H2 migration 各增加同版本 RBAC 建表脚本；建立唯一键、外键/逻辑约束所需索引。迁移仅建表和菜单，不连接或修改真实数据库。
2. 新增社区 RBAC entity、mapper、service。所有角色、用户和绑定查询限定当前 `oid`；当前 MVP 固定单组织 `oid=1`，但表和查询保留 `oid` 字段。
3. 启动时在空 RBAC 库中创建只读“系统管理员”角色，并把现有 substitute admin 密码哈希迁入管理员用户。密码使用项目现有 Bouncy Castle BCrypt，不新增认证框架或自定义密码算法。
4. 把 substitute 登录和用户信息改为数据库用户；JWT 增加 `exp` 和 `authVersion`，继续使用现有 HMAC secret、请求头和过滤器。
5. 实现现有 `RoleApi`、所需 `UserApi`、`AuthApi` 契约。角色创建/编辑/删除、授权和用户绑定全部要求系统管理员或对应管理权限，服务层再次校验，不能只依赖控制器注解。
6. 增加社区权限拦截器/切面，识别实现类所实现接口上的 `@DeApiPath` 和 `@DePermit`。无登录身份、无菜单权限、表达式解析失败均拒绝。分享/嵌入上下文禁止管理接口。
7. `/menu/query` 对普通用户按角色菜单权限过滤；系统管理员返回完整可用菜单。
8. 权限或角色绑定变化后在同一事务内增加受影响用户的 `auth_version`。用户禁用、删除、密码重置也使旧 token 失效。
9. 最后管理员保护放在事务内执行：系统管理员角色不可删除；删除/禁用管理员用户、解绑管理员角色、撤销管理员管理权限前锁定并统计可登录管理员，结果不能小于 1。

### 前端

1. 新增系统设置下的用户管理和角色管理页面，复用 `src/api/user.ts`、`src/api/auth.ts` 及 Element Plus 现有组件风格。
2. 角色页支持增删改、成员绑定、菜单权限树；用户页支持基本增改、启停和角色分配。普通用户不会收到这些菜单。
3. 保留现有动态路由和按钮权限机制；401 清理登录态并重新登录，403 显示现有无权限提示。
4. 增加中英文及繁体中文菜单文案，不改分享和嵌入页面交互。

## 验证与验收

### 自动化

- 后端服务/控制器测试：
  - 管理员创建角色、授权、绑定用户成功。
  - 未登录请求受保护接口返回 401。
  - 普通用户直接调用角色、授权、用户绑定接口返回 403。
  - 普通用户提交包含管理员角色的自我编辑请求被拒绝。
  - 撤销权限后旧 token 下一请求返回 401，新 token 无对应功能权限返回 403。
  - 删除、禁用、解绑或降权最后一个可登录管理员均失败且事务回滚。
  - 跨 `oid` 的用户/角色 ID 操作被拒绝。
  - 分享 token 不能访问管理接口；现有分享查看冒烟测试保持通过。
- 前端：在 `core/core-frontend` 执行 `npm run ts:check`。不运行会自动改文件的全量 lint，除非先限定范围。
- 后端：先检查 core POM/profile/test skip，再从 `core` reactor 或明确模块运行新增测试；需要显式覆盖 `maven-surefire-plugin` 的 `skip=true`。根 Maven reactor 只包含 `sdk`，不能作为 core 验证结果。

### 手工/接口场景

使用本地测试配置和隔离数据库验证正常授权、未登录访问、直接调用无权限接口、自我提权、权限撤销和最后管理员保护。未经单独授权不连接真实数据库或执行 Flyway migration。

## 兼容性与回滚

- distributed/X-Pack 模式不注册社区 RBAC 控制器、拦截器和初始化器。
- 第一次启用会强制旧社区会话重新登录；API URL 和现有 DTO 尽量保持不变。
- 分享链接保留现有查看路径，但管理接口一律拒绝分享/嵌入 token。
- 回滚代码前关闭社区 RBAC 开关；新增表可保留。Flyway 不做自动降级，生产回滚需使用上线前数据库备份。
- 如果迁移前发现目标数据库已有同名表或 X-Pack `per_*` 数据需要接管，停止迁移并重新确认映射方案。

## 已知限制

- MVP 只承诺菜单和明确标注的业务操作权限，不包含行列数据权限、角色继承或自定义策略。
- standalone 社区版为目标；desktop 模式当前绕过登录并固定管理员，不在本期支持多用户。
- 社区源码缺少闭源 X-Pack 的权限语义实现，只能按公开注解和 DTO 保持契约，不能保证内部表结构与 X-Pack 可互换。
