# DataEase 数据迁移 / 导入导出说明

DataEase 的数据可以迁移，按粒度分两类：

- **整机迁移**：把元数据库 + 引擎库 + 数据文件整体搬走，适合换服务器、备份恢复。
- **内容级导入导出**：在页面上把单个仪表板/大屏/应用导出成文件，再在另一套环境导入，适合跨环境分享。

## 一、DataEase 的数据都存在哪

| 类别 | 内容 | Docker/离线安装（standalone） | 本地开发（desktop profile） |
| --- | --- | --- | --- |
| 元数据库 | 用户、组织、数据源配置、数据集、仪表板、大屏、模板、定时任务等 | MySQL 库 `dataease`（`/opt/dataease2.0/data/mysql`） | H2 文件 `core/core-backend/data/desktop.mv.db` |
| 引擎库 | Excel/API 等数据源抽取后的明细数据 | 由 `core_de_engine` 表配置（默认指向内置 MySQL） | H2 文件 `core/core-backend/data/desktop_data.mv.db` |
| 静态资源 | 仪表板里上传的图片、背景等 | `/opt/dataease2.0/data/static-resource` | 默认 `/opt/dataease2.0/data/static-resource/` |
| 其它文件 | 地图 `geo`/`map`、外观 `appearance`、字体 `font`、导出中心文件 `exportData`、插件 `plugin`、国际化 `i18n` | `/opt/dataease2.0/data/*` | 默认同左（`dataease.path.*` 配置） |
| 驱动 | 内置驱动 / 自定义驱动 | `/opt/dataease2.0/drivers`、`custom-drivers` | `dataease.path.driver`（当前为 `../../drivers`） |
| 配置 | `application.yml`、分享密钥 `share-secret.json` 等 | `/opt/dataease2.0/conf` | `src/main/resources/application*.yml` |

> 只迁移数据库、不迁移 `static-resource` 等目录，会导致仪表板图片、自定义地图、字体丢失。

## 二、整机迁移

### 1. Docker/离线安装版：用 `dectl`（推荐）

安装目录里的 `dectl` 自带备份/还原功能（见 `installer/dectl` 中的 `backup` / `restore`）：

```bash
# 旧机器：切到安装目录之外的目录执行
cd /tmp
dectl backup            # 会询问是否停服，建议选 Y 以保证数据一致
# 生成 dataease-backup-YYYYMMDD_HHMMSS.tar.gz

# 新机器：先安装 **相同版本** 的 DataEase，再还原
dectl restore /path/to/dataease-backup-xxxx.tar.gz
```

原理：`backup` 会把整个 `/opt/dataease2.0`（包含内置 MySQL 数据目录、data、conf，排除日志）打成 tar 包；`restore` 会停止服务、清空 `data/mysql`、解压覆盖，然后再启动服务。

### 2. 手动迁移（外置 MySQL 或想自己控制）

```bash
# 1) 停止 DataEase，避免迁移过程中有写入
dectl stop

# 2) 导出元数据库（如果引擎库是独立的库，也照此导出）
mysqldump -h <host> -u <user> -p --single-transaction --routines dataease > dataease.sql

# 3) 打包数据目录和配置
tar -zcf de-files.tar.gz -C /opt/dataease2.0 data/static-resource data/geo data/map \
    data/appearance data/font data/plugin data/i18n conf custom-drivers

# 4) 新环境：建库导入、解压文件，并在 conf/application.yml 中改好数据库连接，再启动
mysql -h <host> -u <user> -p dataease < dataease.sql
tar -zxf de-files.tar.gz -C /opt/dataease2.0
```

### 3. 本地开发（desktop / H2）

H2 是文件数据库，**先停后端**，再复制下面两个文件到新位置即可：

```
core/core-backend/data/desktop.mv.db        # 元数据
core/core-backend/data/desktop_data.mv.db   # 引擎数据
```

如果想要一份可读的 SQL 备份，可以用 H2 自带的工具导出（H2 jar 的版本要和项目使用的版本一致）：

```bash
java -cp h2-<version>.jar org.h2.tools.Script \
  -url "jdbc:h2:./data/desktop" -user sa -password <password> -script desktop-backup.sql
# 还原：org.h2.tools.RunScript -url ... -script desktop-backup.sql
```

## 三、内容级导入导出（页面操作）

| 功能 | 入口 | 产出文件 | 导入方式 |
| --- | --- | --- | --- |
| 导出为模板 | 仪表板/大屏 → 更多 → 导出为模板 | `xxx-TEMPLATE.DET2` | 新建仪表板/大屏时选择上传模板（只接受 `.DET2`）；或在“模板管理 → 导入模板”中导入 |
| 导出为应用 | 仪表板/大屏 → 更多 → 导出为应用（`AppExportForm`） | `xxx-APP.DET2APP` | “模板管理 → 导入模板”（接受 `.DET2,.DET2APP`），再用“使用模板新建”，在应用页面（`DeAppApply`）中绑定数据源 |
| 图表数据导出 | 图表右上角 → 导出 Excel / 导出 Excel（带格式） | `.xlsx` | — |
| 数据集导出 | 数据集 → 数据集导出 | 在“数据导出中心”异步生成，完成后下载 | — |
| 数据导入 | 数据源 → 新建 Excel/CSV 数据源 | — | 上传文件，数据写入引擎库 |

文件格式说明（见 `core-frontend/src/utils/imgUtils.ts`）：`.DET2` / `.DET2APP` 本质上是一个 JSON，包含画布样式、组件配置、图表配置、**当前图表的结果数据快照**、静态资源（图片 base64），应用包还会额外带上 `appData`（数据集/数据源结构）。它**不是**完整的数据库备份，导入后：

- 模板只保留样式和快照数据，需要重新关联数据集；
- 应用会重建数据集结构，但数据源连接需要在新环境里重新填写或绑定，真实数据仍然来自你的数据库。

## 四、注意事项

1. **版本要一致**：建议新旧环境使用同一个 DataEase 版本做迁移。元数据库表结构由 Flyway 管理（`db/migration`、`db/desktop`），只能从低版本向高版本自动升级，不能降级。
2. **desktop（H2）和 standalone（MySQL）之间不能直接互导**：两者的元数据库类型和 Flyway 脚本不同，仓库里也没有现成的转换工具。跨模式迁移建议用“导出为应用 / 模板”的方式，按内容逐个迁移。
3. **数据源连通性**：迁移后，新机器必须能访问原来的业务数据库（IP、白名单、驱动）。自定义驱动目录要一起迁移。
4. **定时任务**：数据集抽取等定时任务存放在元数据库里，迁移后会在新环境继续执行。如果旧环境还在运行，记得停掉，避免重复抽取。
5. **敏感信息**：备份包和 SQL 文件里包含数据源账号等配置，请妥善保管，不要提交到 Git。
