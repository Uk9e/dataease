# 鉴权改造 / 角色管理 / 嵌入功能：评估与实施（升级）计划

> 适用分支：`dev-v2`（DataEase v2.10.x 开源版）
> 关联文档：[feature-estimate.md](./feature-estimate.md)（逐项工作量明细）
> 编写日期：2026-10-28。工作量是基于代码阅读的估算，未做原型验证，误差按 ±20% 考虑。

---

## 第一部分：评估结论

### 1. 目标

| 编号 | 功能 | 说明 |
| --- | --- | --- |
| F1 | 快速跳转（单点登录） | 用户在其他系统点一个链接，免登录进入 DataEase，可直接打开指定仪表板 |
| F2 | 角色管理 | 多用户 + 角色；按角色控制菜单、仪表板/大屏/数据集/数据源的查看、管理、导出 |
| F3 | 内嵌到其他系统 | 把仪表板/大屏/单图表用 iframe 或 div 嵌入其他系统，不需要再次登录，并按访问者权限显示 |

### 2. 现状

| 方面 | 开源版现状 | 关键位置 |
| --- | --- | --- |
| 登录 | 只允许 `admin`；**密码校验被注释，任意密码可登录**；JWT 不过期 | `core-backend/.../substitute/permissions/login/SubstituleLoginServer.java:36-41` |
| token 校验 | `TokenFilter` 只解码不验签；`CommunityTokenFilter` 在无许可证时验签 | `sdk/common/.../auth/filter/` |
| 用户 | 没有用户管理代码，当前用户固定为 uid=1 | `SubstituteUserServer.java`、`AuthUtils.isSysAdmin` |
| 权限接口 | `UserApi`、`RoleApi`、`AuthApi`、`EmbeddedApi` 等只有接口，没有实现 | `sdk/api/api-permissions` |
| 接口鉴权 | `@DePermit` 共 68 处，无任何处理代码（不生效） | `sdk/common/.../auth/DePermit.java` |
| 菜单 | 返回全部菜单，不按权限过滤 | `core-backend/.../menu/manage/MenuManage.java` |
| 前端权限 | 已支持：菜单路由由后端生成；资源节点 `weight` 控制按钮；`v-permission` 指令 | `core-frontend/src/router/establish.ts`、`src/directive/Permission` |
| 嵌入（前端） | 已有 div 嵌入 SDK `DataEaseBi`、`X-EMBEDDED-TOKEN` 请求头、预览页白名单、postMessage | `core-frontend/src/pages/panel/main.ts`、`src/config/axios/service.ts` |
| 嵌入（后端） | `X-EMBEDDED-TOKEN` 无校验；`/embedded/initIframe` 无实现 | `AuthConstant.java`、`WhitelistUtils.java` |
| 分享链接 | 公共链接 + ticket 可用；iframe 中无许可证时被拒绝 | `core-backend/.../share/manage/XpackShareManage.java:249` |

**可复用的数据库表结构**：导入的原服务器数据库中已有企业版建的 `per_*` 表（`license` 表为空，说明没有激活企业版）。开源代码里已有 SQL 直接查询其中几张表（`ExtDataVisualizationMapper.xml`、`DataSetAssistantMapper.java`），所以新功能**沿用这些表结构**，可以和现有 SQL 兼容。

| 表 | 用途 | 主要字段 |
| --- | --- | --- |
| `per_user` | 用户 | id, account, pwd, name, email, phone, enable, origin, default_oid, pwd_update_time |
| `per_role` | 角色 | id, name, desc, level, readonly, org_id, pid |
| `per_user_role` | 用户-角色 | uid, rid, oid |
| `per_auth_menu` | 角色-菜单权限 | rid, resource_id(菜单ID), weight(0无/1查看/2授权) |
| `per_busi_resource` | 业务资源索引 | id, name, rt_id(资源类型), org_id, pid, root_path, leaf, creator |
| `per_auth_busi_role` | 角色-资源权限 | rid, resource_id, resource_type, weight, inherit, ext |
| `per_auth_busi_user` | 用户-资源权限 | 同上，按用户 |
| `per_embedded_instance` | 嵌入应用 | name, app_id, app_secret, domain, secret_length |

已确认 `rt_id = 3` 为数据集、`rt_id = 4` 为数据源（见 `DataSetAssistantMapper.java`）；仪表板、大屏的编号需要在开发时确认。

**`@DePermit` 表达式语义**（从 68 处用法归纳）：

- 表达式是 SpEL，`#p0` 表示方法第 1 个参数，例如 `#p0.id + ':manage'`。
- 后缀是权限级别：`read`（查看）、`manage`（管理）、`export`、`export_view`、`export_detail`。
- `m:read` / `m:manage` 是菜单权限。
- `busiFlag` 指定资源类型（如 `#p0.type`）。
- 数组内多个表达式是“与”关系（注解注释写明“仅考虑 &，不考虑 ||”）。

### 3. 方案选择

| 功能 | 推荐方案 | 备选 |
| --- | --- | --- |
| F1 | **签名票据跳转**：对方后端用共享密钥签发 60 秒有效的 JWT 票据，DataEase 验签后签发自己的 token | 对方已有认证中心时用 OIDC / CAS |
| F2 | **菜单权限 + 资源权限**（仪表板、大屏、数据集、数据源） | 只做菜单级；行列数据权限后续再做 |
| F3 | **嵌入应用**：AppId + 密钥，嵌入 token 携带访问者账号，按其角色权限显示 | 分享链接 + iframe（快，但以分享人身份查数，且要绕过商业版限制） |

### 4. 工作量汇总

| 组合 | 内容 | 人天 | 工期（前后端各 1 人） |
| --- | --- | --- | --- |
| 精简版 | 账号体系 + 票据跳转 + 菜单级角色 + 分享链接嵌入 + 测试 | 35-40 | 4-5 周 |
| **标准版（本计划采用）** | 账号体系 + 票据跳转 + 菜单/资源权限 + 嵌入应用 + 测试 | 76-85 | 约 10 周 |
| 完整版 | 标准版 + 行列数据权限 + OIDC/CAS | 95-110 | 11-13 周 |

后端在关键路径上，多 1 名后端可把标准版压到 6-7 周。

### 5. 主要风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| `@DePermit` 处理逻辑闭源，只能从用法反推 | 资源权限工期可能超出 | 阶段 3 第 1 周先做切面原型和 10 个典型接口，再评估剩余工作 |
| 改动 `sdk/common` 过滤器，后续合并官方版本会冲突 | 每次升级要人工合并 | 新代码放独立包；改动原文件列入清单；见第二部分第 8 节 |
| 新增名为 `loginServer` 的 Bean 会让 `CommunityTokenFilter` 调用不存在的 `apisixCacheManage` | 启动或鉴权失败 | 不用这个 Bean 名，或同步修改 `CommunityTokenFilter` |
| 以后安装企业版 X-Pack 会与自研功能冲突（同一批表、同名接口） | 两套实现不能共存 | 迁移脚本用 `CREATE TABLE IF NOT EXISTS`；自研功能用配置开关，可整体关闭 |
| GPLv3 许可证 | 分发给第三方需开源 | 仅内部使用不受影响；对外分发前确认合规 |

---

## 第二部分：实施计划

### 1. 前提和待确认事项

开始前需要确认（会影响范围和工期）：

1. 其他系统的后端能否按约定签发票据？是否已有统一认证（CAS、OIDC、企业微信、钉钉）？
2. 角色权限粒度：菜单 + 资源（本计划默认），是否需要行列数据权限？
3. 嵌入对象：整个系统，还是单个仪表板/图表？用 iframe 还是 div？
4. 是否需要多组织？本计划按单组织（oid=1）。
5. 是否评估过采购企业版？

人员：后端 1 名、前端 1 名，测试由开发兼任或在阶段 5 投入 1 名测试。

### 2. 总体排期（标准版，约 10 周）

| 周 | 后端 | 前端 | 里程碑 |
| --- | --- | --- | --- |
| 第 1 周 | 阶段 0：安全修复、工程准备；阶段 1：用户表、密码哈希、登录替换 | 阶段 0：环境；阶段 1：用户管理页 | 第 1 周三：安全修复上线 |
| 第 2 周 | 阶段 1：token 过期/验签、用户接口、管理员判断改造 | 阶段 1：用户管理页、修改密码、联调 | **M1：多用户登录可用** |
| 第 3 周 | 阶段 2：SSO 票据接口、防重放、用户映射 | 阶段 2：`/sso` 路由；阶段 3：角色列表页 | **M2：从其他系统一键跳转** |
| 第 4 周 | 阶段 3：权限表迁移、角色接口、菜单过滤 | 阶段 3：角色成员、菜单权限树 | |
| 第 5 周 | 阶段 3：`@DePermit` 切面 | 阶段 3：资源授权页 | |
| 第 6 周 | 阶段 3：资源树过滤、weight 计算 | 阶段 3：资源授权页、按钮显隐联调 | |
| 第 7 周 | 阶段 3：取数/导出/复制/删除接口校验、权限缓存 | 阶段 4：iframe 嵌入入口 | **M3：角色权限可用** |
| 第 8 周 | 阶段 4：嵌入应用表/接口、嵌入 token 校验 | 阶段 4：嵌入应用管理页、div 嵌入 | |
| 第 9 周 | 阶段 4：`initIframe`、CORS、`frame-ancestors` | 阶段 4：宿主页示例、联调 | **M4：嵌入可用** |
| 第 10 周 | 阶段 5：安全测试、回归、上线 | 阶段 5：回归、文档 | **M5：正式上线** |

### 3. 阶段 0：安全修复和工程准备（2 天）

| 任务 | 负责 | 交付物 |
| --- | --- | --- |
| 恢复 `SubstituleLoginServer` 的密码校验；JWT 加过期时间（使用现有配置 `dataease.login_timeout`，单位分钟） | 后端 | 代码 + 上线 |
| 新建开发分支 `feature/auth-role-embed`（基于 `dev-v2`） | 后端 | 分支 |
| 约定代码位置：后端 `io.dataease.custom.{user,sso,role,embedded}`，前端 `src/views/system/{user,role,embedded}` | 全员 | 约定 |
| 约定迁移脚本：放在 `db/migration`，版本号用 `V2.10.20.9xx__custom_*.sql`，避免和官方版本号冲突；建表一律 `CREATE TABLE IF NOT EXISTS` | 后端 | 约定 |
| 增加功能开关：`dataease.custom.user.enabled`、`.sso.enabled`、`.permission.enabled`、`.embedded.enabled`，默认关闭 | 后端 | 配置 |
| 本地环境：standalone 模式 + 本地 MySQL（已用导入数据搭好） | 全员 | 环境 |

验收：任意密码不能再登录；token 超时后跳回登录页。

### 4. 阶段 1：账号体系（约 10 人天，第 1-2 周）

后端：

1. 实体与 Mapper：`per_user`（沿用现有表结构）。
2. 密码存储：使用 BCrypt。项目已依赖 BouncyCastle（`bcprov-jdk15to18`），可用 `org.bouncycastle.crypto.generators.OpenBSDBCrypt`，不需要新增依赖。
3. 首次启动把 `substitule.json` 中的 admin 密码迁移为 `per_user` 中 id=1 的记录。
4. 新登录实现替换 `SubstituleLoginServer`：校验账号、启用状态、密码；签发 JWT（claims：`uid`、`oid`、`exp`）。
5. token 校验统一：在 `TokenFilter` 中验签和校验过期，不再依赖许可证状态分支。
6. 用户接口：分页查询、新增、编辑、启停用、重置密码、删除；当前用户信息替换 `SubstituteUserServer`。
7. 管理员判断：`AuthUtils.isSysAdmin` 从 “uid=1” 改为 “拥有系统管理员角色”（阶段 3 前先保持 uid=1）。

前端：

1. 用户管理页 `src/views/system/user`：列表、搜索、新增/编辑弹窗、启停、重置密码。
2. 在 `core_menu` 增加菜单项（迁移脚本）并配置国际化（`zh-CN.ts`、`en.ts`、`tw.ts`）。
3. 登录页错误提示对接（账号禁用、密码错误、token 过期）。

验收：

- 能创建多个用户，并用各自密码登录；
- 禁用用户无法登录、已登录的会话在下次请求时失效；
- 伪造或篡改 token 返回 401。

### 5. 阶段 2：快速跳转 / 单点登录（约 6 人天，第 3 周）

票据约定（提供给对方系统）：

| 项 | 约定 |
| --- | --- |
| 格式 | JWT，HS256，密钥由 DataEase 管理员生成并线下交给对方 |
| 必填 claims | `iss`（来源系统编码）、`sub`（用户账号）、`exp`（≤ 60 秒）、`jti`（随机数，防重放） |
| 可选 claims | `name`、`email`（自动创建用户时使用） |
| 跳转地址 | `https://<bi域名>/#/sso?ticket=<JWT>&redirect=<站内相对路径>` |

后端：

1. 接口 `POST /sso/exchange`，加入 `WhitelistUtils` 白名单。
2. 校验：签名、过期、`jti` 未使用（存 ehcache，保留 2 分钟）、来源系统已启用。
3. 用户映射：按 `sub` 匹配 `per_user.account`；不存在时按配置决定“自动创建（赋默认角色）”或“拒绝”。
4. 签发 DataEase token 返回前端。
5. 来源系统配置：编码、密钥、是否自动建用户、默认角色（配置文件即可，不做页面）。

前端：

1. 新增路由 `/sso`，加入 `src/permission.ts` 白名单。
2. 读取 `ticket` 调用交换接口，`userStore.setToken` 后跳转 `redirect`。
3. `redirect` 只允许站内相对路径（以 `/` 开头、不含 `//`），防止开放重定向。

验收：

- 正确票据 1 秒内进入目标仪表板；
- 过期、篡改、重复使用的票据都被拒绝；
- `redirect=https://evil.com` 不会跳到外站。

### 6. 阶段 3：角色管理（约 33-37 人天，第 3-7 周）

后端：

1. 迁移脚本：`per_role`、`per_user_role`、`per_auth_menu`、`per_busi_resource`、`per_auth_busi_role`、`per_auth_busi_user`（沿用现有结构）；内置“系统管理员”只读角色。
2. 资源索引：仪表板、大屏、数据集、数据源在新增、移动、重命名、删除时同步写 `per_busi_resource`；对已有数据写一次性初始化脚本。
3. 角色接口：增删改查、分配用户、菜单授权、资源授权（按角色查、按资源查）。
4. 菜单过滤：`MenuManage` 按当前用户角色的 `per_auth_menu` 过滤，管理员不过滤。
5. `@DePermit` 切面：
   - 解析 SpEL，得到资源 ID 和所需级别；
   - `m:` 开头查菜单权限，其他查资源权限；
   - 多个表达式全部满足才放行，否则返回现有的 “无权限” 错误码（前端已处理 `DE-FORBIDDEN-FLAG`）；
   - 先用 10 个典型接口做原型（第 5 周前 2 天），再铺开剩余接口。
6. 资源树与 weight：四类资源树只返回有权限的节点，并按角色权限计算 `weight`。前端现有约定：`weight >= 7` 可管理，`weight = 9` 可完整导出，`1` 只能查看。
7. 取数接口（图表数据、数据集预览）、导出、复制、移动、删除逐一确认受控。
8. 权限缓存：使用 ehcache 已预留的 `de_v2_user_roles`、`de_v2_role_menu_pers`、`de_v2_role_busi_pers`、`de_v2_user_busi_pers`；授权变更时清除对应缓存。

前端：

1. 角色管理页 `src/views/system/role`：角色列表、新增/编辑、成员分配。
2. 菜单权限树：勾选菜单，区分“查看”“授权”。
3. 资源授权页：两种视图（选角色看资源、选资源看角色），权限级别下拉（查看 / 导出 / 管理）。
4. 无权限状态：空树提示、按钮隐藏（现有 `weight` 逻辑）联调。

验收：

- 普通用户只看到被授权的菜单和资源；
- 直接调用未授权资源的接口返回无权限（不能只靠前端隐藏）；
- 修改授权后，该用户刷新页面即生效；
- 管理员不受限制。

### 7. 阶段 4：嵌入到其他系统（约 20-23 人天，第 7-9 周）

接入流程：

```
宿主系统后端 ──(用 AppSecret 签发嵌入 token：appId、account、exp)──▶ 宿主页面
宿主页面 ──iframe: https://<bi>/#/preview?dvId=xxx&embeddedToken=...──▶ DataEase
       或 div: new DataEaseBi('Dashboard', {token, dvId, baseUrl}).initialize()
DataEase 前端 ──请求头 X-EMBEDDED-TOKEN──▶ DataEase 后端校验，按 account 的角色权限返回数据
```

后端：

1. 嵌入应用表 `per_embedded_instance`（沿用现有结构）和管理接口：新建应用、重置密钥、设置允许域名。
2. `TokenFilter` 增加 `X-EMBEDDED-TOKEN` 分支：用对应应用密钥验签、校验过期、按 `account` 建立用户上下文；嵌入 token 只允许访问预览、取数类接口（参照分享链接的 `LinkInterceptor` 做法）。
3. 实现 `/embedded/initIframe`：返回嵌入初始化信息。
4. 按应用域名设置响应头 `Content-Security-Policy: frame-ancestors <域名列表>`（防点击劫持）；div 嵌入需要的 CORS 同样只放行这些域名。

前端：

1. iframe 入口：替代闭源的 `Entrances` 插件，从 URL 读取嵌入 token，调用 `embeddedStore.setIframeData`，渲染仪表板、大屏或单图表。
2. div 嵌入：用现有 `DataEaseBi` 和 `npm run build:flush` 生成的加载脚本，调通跨域 `baseUrl`。
3. 嵌入应用管理页 `src/views/system/embedded`。
4. 对接文档和宿主页示例（HTML + 后端签名示例代码）。

验收：

- 允许的域名可以嵌入，其他域名被浏览器拒绝；
- 嵌入时不需要登录，显示的数据符合访问者的角色权限；
- 嵌入 token 不能用于调用管理类接口；
- 外部参数（`outerParams`）可以传入并生效。

### 8. 阶段 5：测试、上线和后续升级（约 7-9 人天，第 10 周）

测试清单：

- 越权：普通用户访问管理员接口、未授权资源 ID；
- token：伪造、过期、篡改 uid、跨系统复用；
- SSO：重放、过期、开放重定向；
- 嵌入：非白名单域名、嵌入 token 调用管理接口；
- 回归：仪表板编辑/预览、数据集、数据源、分享链接、导出、定时任务。

上线步骤：

1. 上线前备份数据库（`mysqldump --single-transaction dataease`）和 `/opt/dataease2.0/conf`。
2. 部署新版本，Flyway 自动执行 `V2.10.20.9xx__custom_*.sql`。
3. 按顺序打开功能开关：用户 → SSO → 权限 → 嵌入，每打开一项做一次冒烟测试。
4. 观察 1 周日志（401/403 数量、慢查询）。

回滚方案：

- 功能问题：关闭对应功能开关，恢复为原有行为；
- 数据问题：Flyway 不支持自动回退，用上线前的备份恢复；
- 新建的表不影响原有功能，回滚后可以保留。

后续跟进 DataEase 官方版本：

1. 维护一份“改动的官方文件清单”（预计：`TokenFilter`、`CommunityTokenFilter`、`WhitelistUtils`、`MenuManage`、`AuthUtils`、`SubstituleLoginServer`、`SubstituteUserServer`、`permission.ts`、资源树相关 Manage 类）。
2. 每次升级：在新分支合并官方 tag → 只重点处理清单内文件的冲突 → 执行上面的测试清单 → 再合入主分支。
3. 新增迁移脚本保持 `V2.10.20.9xx` 编号段，不和官方编号交叉。

### 9. 交付物清单

| 交付物 | 位置 |
| --- | --- |
| 源代码（独立包 + 改动清单） | `feature/auth-role-embed` 分支 |
| 数据库迁移脚本 | `core-backend/src/main/resources/db/migration/V2.10.20.9xx__custom_*.sql` |
| SSO 对接文档（票据格式、示例代码） | `docs/sso-integration.md`（阶段 2 产出） |
| 嵌入对接文档（iframe/div 示例、签名示例） | `docs/embedded-integration.md`（阶段 4 产出） |
| 测试报告 | 阶段 5 产出 |
| 运维手册（开关、备份、回滚） | 阶段 5 产出 |
