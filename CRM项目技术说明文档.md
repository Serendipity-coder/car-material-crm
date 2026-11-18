# CRM 项目技术说明文档

> 本文档基于项目实际代码梳理，涵盖整体视觉风格、登录注册业务逻辑、三大组织管理页面（员工 / 部门 / 角色权限）的设计与实现，以及 RBAC 权限体系和部门多级树方案，便于新成员快速理解与复刻前端风格。

---

## 一、项目概述

### 1.1 技术栈

| 端 | 技术 |
|---|---|
| 前端 | Vue 3.5 + Vite 6 + TypeScript 5.7 + Element Plus 2.9 + Pinia 2.3 + Vue Router 4.5 + ECharts 5.6 + Axios + dayjs |
| 后端 | Spring Boot 3.5.16 + MyBatis 3.0.4 + MySQL 8.0.46 + Redis 7 + 容联云短信（Cloopen） |
| 构建 | 前端 `pnpm` / `npm`，后端 Maven（JDK 21） |

### 1.2 目录结构

```
crm/
├── fronted/                          # 前端（Vue 3）
│   └── src/
│       ├── api/                      # API 层：client.ts / mock.ts / types.ts / organization-mock.ts
│       ├── layouts/AppShell.vue      # 应用外壳（侧边栏 + 顶栏 + 内容区）
│       ├── router/index.ts           # 路由与权限守卫
│       ├── views/                    # 页面：Login / Register / Employees / Departments / Roles ...
│       ├── permissions.ts            # 前端权限指令与权限码管理
│       ├── utils/                    # 工具（日期 / 组织树）
│       ├── style.css                 # 全局样式与主题变量
│       └── navigation.css            # 侧边栏导航样式
└── backend/                          # 后端（Spring Boot）
    └── src/main/java/com/crm/
        ├── controller/               # Auth / Sms / SysUser / SysDept / SysRole / SysMenu
        ├── service/                  # 业务逻辑（Auth / SysUser / SysDept / SysRole / SysMenu / Sms）
        ├── mapper/                   # MyBatis Mapper 接口 + XML
        ├── pojo/                     # entity / dto / vo
        ├── security/                 # JwtUtil / Pbkdf2 / AuthInterceptor / PermissionService
        └── config/                   # WebConfig / CloopenSmsProperties
```

### 1.3 当前实现范围

- **已实现（走数据库）**：登录 / 注册入口、员工管理、部门架构、角色权限。
- **未实现（前端 Mock 写死）**：客户、联系人、线索、商机、跟进记录、待办任务、数据概览（首页仪表盘）等业务模块。
- 前端通过 `VITE_USE_MOCK` 环境变量切换数据源：`true` 走前端 Mock，`false` 走真实后端。

---

## 二、整体 UI 风格（可直接复刻的规范）

### 2.1 设计基调

一款**现代 SaaS 管理后台**风格，采用「深绿 + 薄荷绿」自然色系，整体清爽、克制、留白充分，弱化边框、强调信息层级。

### 2.2 色彩规范（CSS 变量，定义于 `src/style.css` 的 `:root`）

| 变量 | 色值 | 用途 |
|---|---|---|
| `--navy` | `#183a32` | 侧边栏背景（深绿） |
| `--ink` | `#253b33` | 主文字（墨绿） |
| `--muted` | `#849187` | 次要文字 / 说明 |
| `--accent` / `--mint` | `#528a70` | 主色（薄荷绿，按钮/高亮/链接） |
| `--accent-soft` | `#e6f1e9` | 主色浅底（头像、图标底、hover） |
| `--canvas` | `#f2f6f1` | 页面背景（米白绿） |
| `--card` | `#fff` | 卡片背景 |
| `--line` | `#e5ece5` | 边框 / 分割线 |
| 成功 | `#528a70` | 与主色一致的「绿色 = 正常/在职/启用」 |
| 警告 | `#c3a065` | 金色 |
| 危险 | `#d26f74` | 红色（停用/删除类） |

Element Plus 主题色通过覆盖 CSS 变量实现（`--el-color-primary` 及其 `light-3/5/7/8/9`、`dark-2` 梯度全部映射到薄荷绿），组件圆角 `--el-border-radius-base: 9px`。

### 2.3 字体

```css
font-family: 'DM Sans', 'Noto Sans SC', sans-serif;      /* 正文 */
font-family: 'Manrope', 'Noto Sans SC', sans-serif;      /* 标题 / 数字 */
```

- 数字（统计值、KPI）用 `Manrope`，`font-weight: 700~800`，`letter-spacing` 负值收紧。
- 中文回退到 `Noto Sans SC`。

### 2.4 目录结构

```
工作台
├─ 经营概览
└─ 我的待办

客户管理
├─ 销售线索
├─ 客户公海
├─ 客户档案
├─ 联系人
└─ 跟进记录

产品管理
├─ 产品分类
├─ 产品档案
└─ 客户价格

销售管理
├─ 销售商机
├─ 报价单
├─ 销售订单
└─ 退换货单

交付与回款
├─ 发货记录
├─ 收款记录
└─ 应收账款

售后管理
├─ 售后工单
└─ 客户回访

数据统计
├─ 销售统计
├─ 客户采购分析
└─ 产品销售分析

系统设置
├─ 员工管理
├─ 部门管理
├─ 角色管理
```

- **侧边栏**：固定定位，宽 `248px`，可折叠到 `78px`（仅保留图标）；背景 `linear-gradient(180deg, #183a32, #17372f)`；菜单项圆角 9px，hover 半透明白，选中态高亮。
- **菜单分组**：按「工作台 / 客户经营 / 销售协作 / 组织管理 / 个人与系统」分块，分组间用细分割线。
- **顶栏**：左侧面包屑（工作台 / 当前页），右侧全局搜索、快捷新建、用户头像下拉菜单。
- **路由标签栏**：记录最近访问页面（最多 8 个），可点击切换、可关闭。
- **响应式**：`1150px` 收缩侧边栏、`900px` 折叠为图标栏、`640px` 移动端抽屉式侧边栏。

### 2.5 页面通用组件模式

每个组织管理页面都遵循统一的「**页头 → 统计卡片 → 主体内容**」三段式：

1. **页头（page-heading）**：左侧小号英文眉题（如 `PEOPLE OPERATIONS`）+ 大标题 + 一句说明；右侧主操作按钮（新建）。
2. **统计卡片（org-stats）**：4 张等宽卡片（`grid-template-columns: repeat(4, 1fr)`），内含「标签 + 大数字 + 脚注」，数字用 `Manrope` 字体、颜色区分（普通墨绿 / 强调薄荷绿 / 金色）。
3. **主体内容**：卡片容器，圆角 13px、边框 `--line`，内部承载表格 / 树 / 详情面板。

---

## 三、登录与注册业务逻辑

### 3.1 登录（账号 + 密码 + 手机号 + 短信验证码，四要素）

#### 业务流程图

```
用户在登录页填写 账号 / 密码 / 手机号
        │
        ▼ 点击「获取验证码」
前端校验 account、phone 非空
        │  POST /api/sms/send-code  { account, phone }
        ▼
后端校验：账号存在 → 手机号是否属于该账号（phone == sys_user.phone）
        │  不匹配 → 友好提示「手机号与账号不匹配」（不发短信）
        ▼
生成 4 位验证码 → 短信
        │  Redis 写入：key = "sms:code:" + 前端手机号，value = 验证码，TTL = 2 分钟
        ▼
用户从固定手机号收到短信，填入验证码
        │  POST /auth/login/code  { account, password, phone, verificationCode }
        ▼
后端四重校验（顺序执行）：
  ① 账号密码正确（PBKDF2 哈希比对）
  ② 手机号归属（phone == sys_user.phone）
  ③ 验证码正确（从 Redis 按 phone 取 value 比对）
  ④ 账号状态正常
        │  全部通过 → 删除 Redis 验证码（一次性），签发 JWT
        ▼
返回 { accessToken, user: { id, name, role, permissionCodes[] } }
        │
        ▼
前端：token 存入 localStorage，权限码存入 localStorage + 内存
      → 跳转到第一个有权限的页面（getFirstPermittedPath）
```

#### 关键设计点

- **验证码手机号接收**：短信统一发送。
- **Redis 的 key 用「前端填的手机号」**：因为每个账号绑定了各自手机号，且登录前已校验「该手机号属于该账号」，所以 key 天然唯一、可隔离不同账号。
- **验证码一次性使用**：登录成功后立即 `redisTemplate.delete(key)`，防止重复使用。
- **密码安全**：PBKDF2-HMAC-SHA256（加盐 + 310000 次迭代），查询接口绝不回传密码。
- **JWT**：HS256 自实现（JDK `HmacSHA256`），`sub` 存用户 id，24 小时有效。

#### 前后端对照

| 环节 | 前端 | 后端 |
|---|---|---|
| 发验证码 | `LoginView.vue` `sendSmsCode()` → `api.auth.sendLoginCode({account, phone})` | `POST /api/sms/send-code` → `SmsServiceImpl.sendVerificationCode(phone, account)` |
| 登录 | `LoginView.vue` `submit()` → `api.auth.loginWithPasswordAndCode(...)` | `POST /auth/login/code` → `AuthService.loginWithCode(...)` |
| 拿用户信息 | `AppShell` 挂载时 `api.auth.me()` | `GET /auth/me` → 返回当前用户 + `permissionCodes` |
| 退出 | `AppShell` `handleCommand('logout')` 清 token + 权限码 | `POST /auth/logout` |

### 3.2 注册（手机号 + 验证码 + 密码）

- 前端 `RegisterView.vue` 已完成：填写手机号 → 获取验证码 → 设置密码 → 提交。
- 注册成功后跳回登录页并自动带出手机号（`?phone=xxx&registered=1`）。
- **后端 `/auth/register` 接口暂未实现**（预留契约：`POST /auth/register`，body `{ phone, verificationCode, password }`），由后续补齐。

### 3.3 账号约定

- 超级管理员：`admin / admin123`
- 其余员工（含 `multirole` 演示账号）：`123456`
- 登录填明文密码，后端自动哈希比对。

---

## 四、权限体系（RBAC）

### 4.1 数据模型

```
sys_user（用户） ──< sys_user_role >── sys_role（角色） ──< sys_role_menu >── sys_menu（菜单/权限）
```

- **用户** 通过 `sys_user_role` 关联多个**角色**（多角色）。
- **角色** 通过 `sys_role_menu` 关联多个**菜单/权限节点**。
- 一个用户的最终权限 = 其所有**启用角色**所绑定权限码的**并集**。

### 4.2 菜单 / 权限节点（`sys_menu`）

菜单分三种类型（`menu_type`）：

| 类型 | 含义 | 示例 |
|---|---|---|
| `M` | 目录（分组） | 系统管理 |
| `C` | 页面（菜单项，对应路由） | 员工管理 |
| `F` | 操作 / 按钮权限 | 新增、编辑、删除、授权 |

每个节点带一个权限标识 `perms`（如 `system:user:list`），操作按钮的 `perms` 形如 `system:user:add` / `system:user:edit` / `system:role:assign` 等。

### 4.3 后端权限控制

1. **接口级注解** `@RequirePerm("system:user:list")` 挂在 Controller 方法上。
2. **拦截器** `AuthInterceptor.preHandle`：
   - 解析 `Authorization: Bearer <token>` → 得到 `userId`；
   - 白名单放行：`/auth/login`、`/auth/login/code`、`/api/sms/send-code`；
   - 加载当前用户及其权限码（`PermissionService.loadCurrentUser`）；
   - 若方法带 `@RequirePerm` 且当前用户无该权限码 → 返回 `403 无权限执行该操作`。
3. **权限码并集**（`SysMenuMapper.selectPermsByUserId`）：`sys_user_role ⋈ sys_role ⋈ sys_role_menu ⋈ sys_menu`，`DISTINCT m.perms`，只取启用角色 + 启用菜单 + 非空 perms。

### 4.4 前端权限控制

1. **权限码存储**（`permissions.ts`）：登录后 `setPermissionCodes(codes)` 写入 `localStorage.crm_permission_codes` + 内存响应式快照。
2. **自定义指令** `v-permission`：`v-permission="'system:user:add'"` 控制按钮/菜单项显隐（无权限直接 `display:none`）。
3. **路由守卫** `router.beforeEach`：
   - 未登录访问受保护页 → 跳登录页并带 `redirect`；
   - 路由 `meta.permission` 校验不过 → 跳 `/403`（数据概览特殊处理：跳第一个有权限的页）。
4. **菜单动态显隐**：侧边栏每个 `el-menu-item` 都挂 `v-permission`，只有拥有对应 `:list` 权限码的菜单才显示。

### 4.5 效果

> **用户 → 角色 → 权限，角色不同权限不同，看到的页面和按钮也不同。**

- 超级管理员（`admin` 角色）：拥有全部 53 个权限节点，看到所有菜单与按钮。
- 销售专员（`sales` 角色）：只有客户/线索/商机等客户经营权限，**看不到「员工管理 / 部门架构 / 角色权限」菜单**，也无法调用对应接口（后端 403 兜底）。
- 多角色用户（如 `multirole`）：权限码为多个角色的并集。

---

## 五、三大页面详解

### 5.1 员工管理（`/employees`，权限 `system:user:list`）

#### 布局

```
┌────────────────────────────────────────────────────────────┐
│  页头：PEOPLE OPERATIONS / 员工管理 / 说明        [新建员工] │
├────────────────────────────────────────────────────────────┤
│  [员工总数] [在职员工] [组织部门] [本月新成员]  ← 4 张统计卡片 │
├────────────────────────────────────────────────────────────┤
│  筛选栏：关键词 / 部门 / 角色 / 状态   [重置]   [批量停用]    │
├────────────────────────────────────────────────────────────┤
│  表格：员工(头像+姓名+账号) | 所属部门(路径) | 职位 | 角色(标签) │
│        | 联系方式 | 状态 | 入职日期 | 操作(档案/编辑/停用)      │
├────────────────────────────────────────────────────────────┤
│  分页                                                       │
└────────────────────────────────────────────────────────────┘
```

#### 业务逻辑

- **列表**：分页查询，支持关键词（姓名/账号/邮箱/电话）、部门、角色、状态多条件筛选。
- **新建 / 编辑**：对话框表单，含真实姓名、登录账号（编辑时禁改）、密码（新建必填≥6 位，编辑留空不修改）、所属部门（下拉，显示部门全路径）、岗位、**角色多选**（`roleIds[]`，`collapse-tags`）、性别、入职日期、电话、邮箱、状态、备注。
- **档案抽屉**：右侧滑出员工详情（组织与访问 / 联系信息 / 备注 / 最近登录 IP）。
- **停用 / 启用**：单个切换 + 批量停用（仅在职可选）。

#### 业务规则（后端 `SysUserService`）

- 登录账号唯一（`selectByUsername` 判重）。
- 初始密码 / 新密码至少 6 位。
- 角色必须存在且为启用状态。
- **超级管理员保护**：不能停用或移除最后一位启用状态的超级管理员。

#### 接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/system/users` | `system:user:list` | 分页列表 |
| GET | `/system/users/summary` | `system:user:list` | 统计摘要 |
| GET | `/system/users/{id}` | `system:user:list` | 详情 |
| POST | `/system/users` | `system:user:add` | 新建 |
| PUT | `/system/users/{id}` | `system:user:edit` | 编辑 |
| PATCH | `/system/users/status` | `system:user:edit` | 批量停用/启用 |

### 5.2 部门架构（`/departments`，权限 `system:dept:list`）

#### 布局（左右分栏 + 树）

```
┌────────────────────────────────────────────────────────────┐
│  页头：ORGANIZATION DESIGN / 部门架构           [新建部门]  │
├────────────────────────────────────────────────────────────┤
│  [组织部门] [启用部门] [组织层级] [在册成员]  ← 4 张统计卡片  │
├──────────────────────────────┬─────────────────────────────┤
│  组织树（可搜索）              │  部门详情                    │
│  ┌─ 总公司                    │  面包屑路径 / 部门名 / 状态   │
│  │  ├─ 研发中心                │  负责人 / 成员数 / 电话 / 邮箱│
│  │  │   ├─ 后端组              │  ───────────────            │
│  │  │   └─ 前端组              │  部门成员表格（直属员工）      │
│  │  └─ 市场部                 │                             │
│  └─ ...                       │                             │
└──────────────────────────────┴─────────────────────────────┘
```

#### 业务逻辑

- **组织树**（`el-tree`）：默认全部展开，点击节点查看详情与直属成员；支持搜索过滤。
- **部门详情**：面包屑路径（`getDepartmentPath`）、名称、状态标签、编号、排序、负责人、成员数、电话、邮箱；下方列出**直属**成员表格（不包含下级部门成员）。
- **新建 / 编辑**：部门名称、上级部门（下拉，展示全路径，**编辑时排除自身及其下级，防环**）、负责人（员工下拉）、排序、电话、邮箱、状态。
- **操作**：新增下级部门、编辑、停用/启用、删除。

#### 业务规则（后端 `SysDeptService`）

- **多级部门**：`parent_id` 关联 + `ancestors` 字段记录完整祖先路径（如 `0,1,3`），支持任意层级向下扩展。
- **防环**：移动部门时校验目标父级不能是自身或其后代（`isSelfOrDescendant`）。
- **ancestors 重算**：移动后递归重算整个子树的祖先路径。
- **删除保护**：有下级部门或有直属员工时禁止删除。

#### 接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/system/depts/tree` | `system:dept:list` | 部门树 |
| POST | `/system/depts` | `system:dept:add` | 新建 |
| PUT | `/system/depts/{id}` | `system:dept:edit` | 编辑 |
| DELETE | `/system/depts/{id}` | `system:dept:remove` | 删除 |

### 5.3 角色权限（`/roles`，权限 `system:role:list`）

#### 布局（左右分栏 + 权限树对话框）

```
┌────────────────────────────────────────────────────────────┐
│  页头：ACCESS CONTROL / 角色权限                [新建角色]  │
├────────────────────────────────────────────────────────────┤
│  [角色总数] [启用角色] [权限节点] [角色关联记录] ← 4 张卡片   │
├────────────────────────────────┬───────────────────────────┤
│  角色列表                       │  角色检查器（ROLE PROFILE） │
│  ┌─ 🔑 销售专员 sales           │  角色名 / roleKey          │
│  │    23 项权限 · 5 人 · 启用中 │  授权节点 / 关联成员 / 状态  │
│  └─ ...                        │  ──────────────────────    │
│  操作：配置权限 / 编辑 / 停用/删除│  授权概览（权限列表预览）     │
│                                │  [编辑角色] [配置权限]      │
└────────────────────────────────┴───────────────────────────┘

配置权限对话框：
  [M 目录] [C 页面] [F 操作] 图例    [全选] [清空]
  ☑ 系统管理
    ☑ 员工管理          (system:user:list)
      ☑ 查询  ☑ 新增  ☑ 编辑  ☑ 停用   ← 按钮级权限（F 节点）
    ☑ 部门架构          (system:dept:list)
    ☑ 角色权限          (system:role:list)
```

#### 业务逻辑

- **角色列表**：展示角色名 + 标识（`roleKey`）、授权范围（`menuIds.length` 项权限）、成员数、状态；点击选中在右侧检查器查看详情。
- **配置权限**：弹出权限树（`el-tree` + `show-checkbox` + `check-strictly` 父子独立勾选），节点按 `M/C/F` 三类着色区分，支持全选 / 清空；保存时收集勾选 + 半选节点 id 提交。
- **授权**：`PUT /system/roles/{id}/menus`，body `{ menuIds[] }`，后端先清空该角色旧关联，再写入新关联（事务）。

#### 业务规则（后端 `SysRoleService`）

- `roleKey` 唯一，格式为「小写字母开头，可含字母/数字/下划线/连字符」。
- **内置超级管理员角色（`admin`）保护**：不能删除、不能停用、不能改标识、授权时必须保留全部权限。
- 已分配给员工的角色不能删除。

#### 接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/system/roles` | `system:role:list` | 角色列表 |
| GET | `/system/roles/{id}` | `system:role:list` | 详情 |
| POST | `/system/roles` | `system:role:add` | 新建 |
| PUT | `/system/roles/{id}` | `system:role:edit` | 编辑 |
| DELETE | `/system/roles/{id}` | `system:role:remove` | 删除 |
| PUT | `/system/roles/{id}/menus` | `system:role:assign` | 配置权限 |
| GET | `/system/menus/tree` | `system:role:list` | 权限树 |

---

## 六、部门多级树实现方案

### 6.1 数据结构

`sys_dept` 表关键字段：

| 字段 | 说明 |
|---|---|
| `dept_id` | 部门主键 |
| `parent_id` | 上级部门 id（`0` 表示顶级） |
| `ancestors` | 祖先路径字符串，如 `0,1,3`（根→父→自己之前的链） |
| `order_num` | 同级排序 |

### 6.2 树构建（`SysDeptService.tree()`）

1. 一次性查出所有部门 `selectAll()`；
2. 装配为 `DeptVO`（含 `children`、`employeeCount`、`leaderName`）；
3. 按 `parent_id` 挂载：找到父节点则加入其 `children`，否则作为根节点返回。
4. 前端 `el-tree` 直接消费该嵌套结构。

### 6.3 关键规则

- **ancestors 自动计算**：新建部门时，`ancestors = 父.ancestors + "," + 父.id`（顶级为 `"0"`）。
- **移动防环**：更新父级时，校验「新父级 ≠ 自身且不在自身后代中」（遍历候选父级的 ancestors 判断）。
- **祖先重算**：父级变化后，递归重算整棵子树每个节点的 ancestors（`recomputeAncestors`）。
- **删除保护**：有子部门或有员工时拒绝删除，需先迁移。
- **路径展示**：前端 `DeptTreeUtil.pathOf` 用 ancestors 拼出「总公司 / 研发中心 / 后端组」形式的完整路径，用于员工列表和部门下拉。

---

## 七、数据库表结构（tcrm 库，6 张表）

| 表 | 说明 | 关键字段 |
|---|---|---|
| `sys_user` | 员工/用户 | `user_id`、`dept_id`、`username`、`password`、`real_name`、`gender`、`phone`、`email`、`avatar`、`position`、`status`、`hire_date`、`last_login_time/ip`、`remark`、`deleted` |
| `sys_dept` | 部门 | `dept_id`、`parent_id`、`ancestors`、`dept_name`、`order_num`、`leader_id`、`phone`、`email`、`status`、`deleted` |
| `sys_role` | 角色 | `role_id`、`role_name`、`role_key`、`sort`、`status`、`remark`、`deleted` |
| `sys_menu` | 菜单/权限 | `menu_id`、`parent_id`、`menu_name`、`menu_type`(M/C/F)、`perms`、`path`、`component`、`icon`、`sort`、`visible`、`status`、`deleted` |
| `sys_user_role` | 用户-角色关联 | `user_id`、`role_id` |
| `sys_role_menu` | 角色-菜单关联 | `role_id`、`menu_id` |

> 表字段采用 snake_case，后端通过 MyBatis `map-underscore-to-camel-case` 自动映射到 camelCase 的 Java 实体。

---

## 八、认证与权限相关接口汇总

| 方法 | 路径 | 说明 | 是否需登录 |
|---|---|---|---|
| POST | `/auth/login` | 账号密码登录（旧接口，仅账号密码） | 否 |
| POST | `/auth/login/code` | 四要素验证码登录 | 否 |
| POST | `/api/sms/send-code` | 发送验证码（校验手机号归属） | 否 |
| POST | `/auth/register` | 注册（**后端未实现**） | 否 |
| POST | `/auth/logout` | 退出登录 | 是 |
| GET | `/auth/me` | 当前用户信息 + 权限码 | 是 |
| PATCH | `/auth/me` | 更新个人资料 | 是 |
| POST | `/auth/password` | 修改密码 | 是 |

> 统一前缀 `/api/v1`；错误通过 `GlobalExceptionHandler` 返回 `{ message }`，业务异常用 `BusinessException` 抛出并友好提示。

---

## 九、环境与启动

| 项 | 说明 |
|---|---|
| MySQL | `localhost:3306/tcrm` |
| Redis | Docker 容器 `text2sql2bi-redis-1`，宿主机端口 **16379** |
| 短信 | 容联云（Cloopen） |
| 后端 | `cd backend && mvn -o spring-boot:run`（端口 8080，context-path `/api/v1`） |
| 前端 | `cd fronted && npm run dev`（端口 3000） |
| 前端切数据源 | `fronted/.env.local` 设 `VITE_USE_MOCK=false` + `VITE_API_BASE_URL=http://localhost:8080/api/v1` |

---

## 十、关键约束与注意事项

1. **密码哈希用 PBKDF2-HMAC-SHA256**（本地无 BCrypt 依赖且不联网下载），后续允许联网可一行替换回 BCrypt。
2. **JWT 为 HS256 自实现**，无第三方 JWT 依赖。
3. **Redis 验证码 key 用「前端手机号」**，登录前已校验手机号归属，故可安全隔离不同账号。
4. **验证码一次性使用**，登录成功即删除；短信平台对同一手机号有 60 秒发送频控。
5. **超级管理员保护**（admin 账号 + admin 角色）贯穿员工与角色两模块，防止误停用/误删。
6. **部门删除/移动有强校验**，防止数据悬挂与成环。
7. 其他业务模块（客户/线索/商机等）后端尚未实现，前端仍为 Mock 数据，属正常状态。
