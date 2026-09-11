import fs from 'node:fs';

const root = 'C:/Users/11931/IdeaProjects/spzx-parent';
const results = JSON.parse(fs.readFileSync(root + '/.ua/tmp/ua-file-extract-results-35.json', 'utf8'));

// ---- metadata ----
const fileMeta = {
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysMenuService.java':
    { summary: '菜单服务接口,定义菜单树查询、权限标识获取与前端路由构建等方法,供系统管理模块契约使用。', tags: ['interface', 'menu', 'service', 'api-contract', 'type-definition'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysNoticeService.java':
    { summary: '通知公告服务接口,定义通知的增删改查方法。', tags: ['interface', 'notice', 'service', 'api-contract'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysOperLogService.java':
    { summary: '操作日志服务接口,定义操作日志的查询、删除与清理方法。', tags: ['interface', 'log', 'service', 'audit'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysPermissionService.java':
    { summary: '权限服务接口,定义根据登录用户获取角色与菜单权限标识的方法。', tags: ['interface', 'permission', 'service', 'security'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysPostService.java':
    { summary: '岗位服务接口,定义岗位的增删改查与用户-岗位关联方法。', tags: ['interface', 'post', 'service', 'api-contract'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysRoleService.java':
    { summary: '角色服务接口,定义角色管理、数据权限范围配置与用户-角色授权方法。', tags: ['interface', 'role', 'service', 'authorization'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysUserOnlineService.java':
    { summary: '在线用户服务接口,定义在线用户的查询与登录用户转换方法。', tags: ['interface', 'online-user', 'service', 'monitoring'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysUserService.java':
    { summary: '用户服务接口,定义用户管理、导入导出、账户唯一性校验等方法。', tags: ['interface', 'user', 'service', 'account'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysConfigServiceImpl.java':
    { summary: '系统参数配置服务实现,提供参数的增删改查、唯一性校验与缓存刷新。', tags: ['service', 'config', 'data-access', 'cache'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java':
    { summary: '部门管理服务实现,处理部门树构建、层级关系维护与数据权限校验。', tags: ['service', 'dept', 'tree', 'data-access'], complexity: 'complex' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDictDataServiceImpl.java':
    { summary: '字典数据服务实现,提供字典明细的增删改查与按类型/值查询标签。', tags: ['service', 'dictionary', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDictTypeServiceImpl.java':
    { summary: '字典类型服务实现,管理字典类型及其查询与缓存加载。', tags: ['service', 'dictionary', 'cache', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysLogininforServiceImpl.java':
    { summary: '登录日志服务实现,记录并查询登录日志,支持批量删除与清理。', tags: ['service', 'login', 'audit', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java':
    { summary: '菜单服务实现,构建菜单树、前端路由并汇集菜单权限标识。', tags: ['service', 'menu', 'tree', 'router'], complexity: 'complex' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysNoticeServiceImpl.java':
    { summary: '通知公告服务实现,提供通知的增删改查与批量删除。', tags: ['service', 'notice', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysOperLogServiceImpl.java':
    { summary: '操作日志服务实现,提供操作日志的查询、删除、详情与清理。', tags: ['service', 'log', 'audit', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPermissionServiceImpl.java':
    { summary: '权限服务实现,根据登录用户聚合角色与菜单权限标识并处理管理员标识。', tags: ['service', 'permission', 'security', 'aggregation'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPostServiceImpl.java':
    { summary: '岗位服务实现,提供岗位的增删改查与用户-岗位关联维护。', tags: ['service', 'post', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java':
    { summary: '角色服务实现,管理角色、数据权限范围与用户-角色、角色-菜单关联。', tags: ['service', 'role', 'authorization', 'data-access'], complexity: 'complex' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserOnlineServiceImpl.java':
    { summary: '在线用户服务实现,按 IP/用户名查询在线用户并转换为在线用户对象。', tags: ['service', 'online-user', 'monitoring', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java':
    { summary: '用户服务实现,支持用户管理、导入导出、账户唯一性校验与角色/岗位关联维护。', tags: ['service', 'user', 'data-access', 'mybatis-plus'], complexity: 'complex' },
  'spzx-modules/spzx-user/pom.xml':
    { summary: 'spzx-user 会员模块的 Maven 构建配置,声明 Nacos/Sentinel/MySQL 等依赖与 Spring Boot 打包插件。', tags: ['configuration', 'maven', 'build-system', 'deployment'], complexity: 'moderate' },
  'spzx-modules/spzx-user/src/main/java/com/spzx/user/SpzxUserApplication.java':
    { summary: '会员模块启动类,加载 Spring Boot 应用上下文并启动 spzx-user 服务。', tags: ['entry-point', 'spring-boot', 'application'], complexity: 'simple' },
  'spzx-modules/spzx-user/src/main/java/com/spzx/user/controller/RegionController.java':
    { summary: '地区控制器,按父编码提供地区树下拉选择接口。', tags: ['controller', 'region', 'api-handler', 'tree'], complexity: 'simple' },
  'spzx-modules/spzx-user/src/main/java/com/spzx/user/controller/SmsController.java':
    { summary: '短信控制器,提供验证码发送接口。', tags: ['controller', 'sms', 'api-handler', 'verification'], complexity: 'simple' },
};

const classMeta = {
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysMenuService.java::ISysMenuService':
    { summary: '菜单服务接口,声明菜单树、权限标识与前端路由构建契约。', tags: ['interface', 'menu', 'service', 'api-contract'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysNoticeService.java::ISysNoticeService':
    { summary: '通知公告服务接口,声明通知增删改查契约。', tags: ['interface', 'notice', 'service'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysOperLogService.java::ISysOperLogService':
    { summary: '操作日志服务接口,声明操作日志查询与清理契约。', tags: ['interface', 'log', 'audit', 'service'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysPermissionService.java::ISysPermissionService':
    { summary: '权限服务接口,声明获取角色/菜单权限标识契约。', tags: ['interface', 'permission', 'security', 'service'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysPostService.java::ISysPostService':
    { summary: '岗位服务接口,声明岗位管理与用户-岗位关联契约。', tags: ['interface', 'post', 'service'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysRoleService.java::ISysRoleService':
    { summary: '角色服务接口,声明角色、数据权限与授权契约。', tags: ['interface', 'role', 'authorization', 'service'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysUserOnlineService.java::ISysUserOnlineService':
    { summary: '在线用户服务接口,声明在线用户查询契约。', tags: ['interface', 'online-user', 'monitoring', 'service'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysUserService.java::ISysUserService':
    { summary: '用户服务接口,声明用户管理与校验契约。', tags: ['interface', 'user', 'service', 'account'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysConfigServiceImpl.java::SysConfigServiceImpl':
    { summary: '系统参数配置服务实现,提供参数增删改查与缓存刷新。', tags: ['service', 'config', 'data-access', 'cache'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java::SysDeptServiceImpl':
    { summary: '部门管理服务实现,处理部门树、层级与数据权限校验。', tags: ['service', 'dept', 'tree', 'data-access'], complexity: 'complex' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDictDataServiceImpl.java::SysDictDataServiceImpl':
    { summary: '字典数据服务实现,提供字典明细增删改查。', tags: ['service', 'dictionary', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDictTypeServiceImpl.java::SysDictTypeServiceImpl':
    { summary: '字典类型服务实现,管理字典类型与缓存加载。', tags: ['service', 'dictionary', 'cache', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysLogininforServiceImpl.java::SysLogininforServiceImpl':
    { summary: '登录日志服务实现,记录并清理登录日志。', tags: ['service', 'login', 'audit', 'data-access'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::SysMenuServiceImpl':
    { summary: '菜单服务实现,构建菜单树与前端路由。', tags: ['service', 'menu', 'tree', 'router'], complexity: 'complex' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysNoticeServiceImpl.java::SysNoticeServiceImpl':
    { summary: '通知公告服务实现,提供通知增删改查。', tags: ['service', 'notice', 'data-access'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysOperLogServiceImpl.java::SysOperLogServiceImpl':
    { summary: '操作日志服务实现,提供日志查询与清理。', tags: ['service', 'log', 'audit', 'data-access'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPermissionServiceImpl.java::SysPermissionServiceImpl':
    { summary: '权限服务实现,聚合角色与菜单权限标识。', tags: ['service', 'permission', 'security', 'aggregation'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPostServiceImpl.java::SysPostServiceImpl':
    { summary: '岗位服务实现,提供岗位增删改查与关联维护。', tags: ['service', 'post', 'data-access'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::SysRoleServiceImpl':
    { summary: '角色服务实现,管理角色、数据权限与授权关联。', tags: ['service', 'role', 'authorization', 'data-access'], complexity: 'complex' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserOnlineServiceImpl.java::SysUserOnlineServiceImpl':
    { summary: '在线用户服务实现,查询并转换在线用户。', tags: ['service', 'online-user', 'monitoring'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::SysUserServiceImpl':
    { summary: '用户服务实现,支持用户管理与导入导出。', tags: ['service', 'user', 'data-access', 'mybatis-plus'], complexity: 'complex' },
  'spzx-modules/spzx-user/src/main/java/com/spzx/user/SpzxUserApplication.java::SpzxUserApplication':
    { summary: '会员模块 Spring Boot 启动类,引导应用上下文.。', tags: ['entry-point', 'application', 'spring-boot'], complexity: 'simple' },
  'spzx-modules/spzx-user/src/main/java/com/spzx/user/controller/RegionController.java::RegionController':
    { summary: '地区控制器,暴露地区树接口。', tags: ['controller', 'region', 'api-handler'], complexity: 'simple' },
  'spzx-modules/spzx-user/src/main/java/com/spzx/user/controller/SmsController.java::SmsController':
    { summary: '短信控制器,暴露验证码发送接口。', tags: ['controller', 'sms', 'api-handler'], complexity: 'simple' },
};

const funcMeta = {
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysConfigServiceImpl.java::selectConfigByKey':
    { summary: '按参数键查询参数配置,用于系统参数快速获取。', tags: ['service', 'config', 'query'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysConfigServiceImpl.java::updateConfig':
    { summary: '更新参数配置并刷新缓存。', tags: ['service', 'config', 'update', 'cache'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysConfigServiceImpl.java::deleteConfigByIds':
    { summary: '批量删除参数配置并清理缓存。', tags: ['service', 'config', 'delete', 'cache'], complexity: 'simple' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java::buildDeptTree':
    { summary: '将部门列表构建为树形结构。', tags: ['service', 'dept', 'tree', 'build'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java::checkDeptDataScope':
    { summary: '校验部门是否在当前用户数据权限范围内。', tags: ['service', 'dept', 'data-scope', 'validation'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java::insertDept':
    { summary: '新增部门并维护父子层级关系。', tags: ['service', 'dept', 'insert', 'hierarchy'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java::updateDept':
    { summary: '更新部门信息并同步子孙节点的祖先路径。', tags: ['service', 'dept', 'update', 'hierarchy'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java::recursionFn':
    { summary: '递归查找指定部门的子部门集合。', tags: ['service', 'dept', 'recursion', 'tree'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDeptServiceImpl.java::getChildList':
    { summary: '获取指定部门下的直接子部门列表。', tags: ['service', 'dept', 'tree', 'query'], complexity: 'simple' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDictTypeServiceImpl.java::selectDictDataByType':
    { summary: '按字典类型查询字典数据集合,供下拉框使用。', tags: ['service', 'dictionary', 'query'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDictTypeServiceImpl.java::deleteDictTypeByIds':
    { summary: '批量删除字典类型并清理缓存。', tags: ['service', 'dictionary', 'delete', 'cache'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysDictTypeServiceImpl.java::updateDictType':
    { summary: '更新字典类型并刷新字典缓存。', tags: ['service', 'dictionary', 'update', 'cache'], complexity: 'moderate' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::selectMenuList':
    { summary: '按菜单条件与用户过滤查询菜单列表。', tags: ['service', 'menu', 'query'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::selectMenuPermsByUserId':
    { summary: '查询用户拥有的菜单权限标识集合。', tags: ['service', 'menu', 'permission', 'security'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::selectMenuPermsByRoleId':
    { summary: '查询角色拥有的菜单权限标识集合。', tags: ['service', 'menu', 'permission', 'role'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::selectMenuTreeByUserId':
    { summary: '按用户构建其可见的菜单树。', tags: ['service', 'menu', 'tree', 'query'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::buildMenus':
    { summary: '将菜单实体列表构建为前端路由树。', tags: ['service', 'menu', 'router', 'build'], complexity: 'complex' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::buildMenuTree':
    { summary: '构建菜单父子树结构。', tags: ['service', 'menu', 'tree', 'build'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::getRouterPath':
    { summary: '根据菜单类型计算前端路由路径。', tags: ['service', 'menu', 'router', 'util'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::getComponent':
    { summary: '根据菜单组件名计算前端组件引用。', tags: ['service', 'menu', 'router', 'util'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::getChildPerms':
    { summary: '递归获取指定父菜单下的子菜单权限树。', tags: ['service', 'menu', 'recursion', 'permission'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::recursionFn':
    { summary: '递归查找菜单子树。', tags: ['service', 'menu', 'recursion', 'tree'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java::getChildList':
    { summary: '获取直接子菜单列表。', tags: ['service', 'menu', 'tree', 'query'], complexity: 'simple' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPermissionServiceImpl.java::getRolePermission':
    { summary: '获取用户在角色中拥有的权限标识。', tags: ['service', 'permission', 'role', 'security'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPermissionServiceImpl.java::getMenuPermission':
    { summary: '获取用户菜单权限标识并处理管理员标识。', tags: ['service', 'permission', 'menu', 'security'], complexity: 'moderate' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPostServiceImpl.java::deletePostByIds':
    { summary: '批量删除岗位并校验是否已分配用户。', tags: ['service', 'post', 'delete', 'validation'], complexity: 'simple' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::selectRolesByUserId':
    { summary: '查询用户拥有的角色列表。', tags: ['service', 'role', 'query', 'user'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::selectRolePermissionByUserId':
    { summary: '查询用户角色权限标识集合。', tags: ['service', 'role', 'permission', 'user'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::checkRoleDataScope':
    { summary: '校验角色是否在当前用户数据权限范围内。', tags: ['service', 'role', 'data-scope', 'validation'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::authDataScope':
    { summary: '为角色分配数据权限范围并保存关联。', tags: ['service', 'role', 'data-scope', 'authorize'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::insertRoleMenu':
    { summary: '保存角色与菜单的关联关系。', tags: ['service', 'role', 'menu', 'relation'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::insertRoleDept':
    { summary: '保存角色与部门的数据权限关联。', tags: ['service', 'role', 'dept', 'relation'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::deleteRoleByIds':
    { summary: '批量删除角色并清理其菜单/部门关联。', tags: ['service', 'role', 'delete', 'cleanup'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java::insertAuthUsers':
    { summary: '为角色批量分配用户。', tags: ['service', 'role', 'user', 'authorize'], complexity: 'simple' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserOnlineServiceImpl.java::loginUserToUserOnline':
    { summary: '将登录用户转换为在线用户对象。', tags: ['service', 'online-user', 'conversion', 'monitoring'], complexity: 'moderate' },

  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::checkUserDataScope':
    { summary: '校验用户是否在当前用户数据权限范围内。', tags: ['service', 'user', 'data-scope', 'validation'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::insertUser':
    { summary: '新增用户并保存角色与岗位关联。', tags: ['service', 'user', 'insert', 'relation'], complexity: 'simple' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::updateUser':
    { summary: '更新用户信息并同步角色、岗位关联。', tags: ['service', 'user', 'update', 'relation'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::insertUserPost':
    { summary: '保存用户与岗位的关联关系,并在异常时回滚事务。', tags: ['service', 'user', 'post', 'relation'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::insertUserRole':
    { summary: '保存用户与角色的关联关系。', tags: ['service', 'user', 'role', 'relation'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::deleteUserByIds':
    { summary: '批量删除用户并清理其角色、岗位关联。', tags: ['service', 'user', 'delete', 'cleanup'], complexity: 'moderate' },
  'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java::importUser':
    { summary: '批量导入用户数据,支持唯一性校验与更新。', tags: ['service', 'user', 'import', 'data-access'], complexity: 'complex' },

  'spzx-modules/spzx-user/src/main/java/com/spzx/user/SpzxUserApplication.java::main':
    { summary: 'Spring Boot 应用入口,启动会员服务。', tags: ['entry-point', 'application', 'bootstrap'], complexity: 'simple' },
  'spzx-modules/spzx-user/src/main/java/com/spzx/user/controller/SmsController.java::sendCode':
    { summary: '发送短信验证码到指定手机号。', tags: ['controller', 'sms', 'verification', 'api-handler'], complexity: 'simple' },
};

// implements pairs (impl class -> interface class), both in-batch
const implementsPairs = [
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysMenuServiceImpl.java', 'ISysMenuService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysMenuService.java'],
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysNoticeServiceImpl.java', 'ISysNoticeService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysNoticeService.java'],
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysOperLogServiceImpl.java', 'ISysOperLogService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysOperLogService.java'],
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPermissionServiceImpl.java', 'ISysPermissionService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysPermissionService.java'],
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysPostServiceImpl.java', 'ISysPostService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysPostService.java'],
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysRoleServiceImpl.java', 'ISysRoleService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysRoleService.java'],
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserOnlineServiceImpl.java', 'ISysUserOnlineService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysUserOnlineService.java'],
  ['spzx-modules/spzx-system/src/main/java/com/spzx/system/service/impl/SysUserServiceImpl.java', 'ISysUserService', 'spzx-modules/spzx-system/src/main/java/com/spzx/system/service/ISysUserService.java'],
];

// ---- find file type ----
function fileType(path) {
  if (path.endsWith('pom.xml')) return 'config';
  return 'file';
}
function nodeType(path) {
  const t = fileType(path);
  return t;
}

// ---- build file nodes ----
const nodes = [];
const fileNodes = new Map(); // path -> node id
for (const path of Object.keys(fileMeta)) {
  const meta = fileMeta[path];
  const type = fileType(path);
  const id = (type === 'config' ? 'config:' : 'file:') + path;
  const node = {
    id,
    type,
    name: path.split('/').pop(),
    filePath: path,
    summary: meta.summary,
    tags: meta.tags,
    complexity: meta.complexity,
  };
  nodes.push(node);
  fileNodes.set(path, id);
}

// ---- build class & function nodes ----
const fnSeen = new Map(); // path::name -> {func, span}
function rememberFn(path, name, startLine, endLine) {
  const key = path + '::' + name;
  const span = endLine - startLine + 1;
  const cur = fnSeen.get(key);
  if (!cur || cur.span < span) fnSeen.set(key, { startLine, endLine, span });
}

for (const res of results.results) {
  const path = res.path;
  if (!fileMeta[path]) continue;
  // classes
  for (const c of res.classes || []) {
    const key = path + '::' + c.name;
    const meta = classMeta[key];
    if (!meta) {
      console.error('MISSING class meta: ' + key);
      continue;
    }
    nodes.push({
      id: 'class:' + path + ':' + c.name,
      type: 'class',
      name: c.name,
      filePath: path,
      lineRange: [c.startLine, c.endLine],
      summary: meta.summary,
      tags: meta.tags,
      complexity: meta.complexity,
    });
    rememberFn(path, c.name, c.startLine, c.endLine); // not used for funcs
  }
  // functions (dedupe overloads by name, keep max span)
  const byName = new Map();
  for (const f of res.functions || []) {
    const span = f.endLine - f.startLine + 1;
    const cur = byName.get(f.name);
    if (!cur || cur.span < span) byName.set(f.name, { startLine: f.startLine, endLine: f.endLine, span });
  }
  for (const [name, f] of byName) {
    if (f.span < 10) continue; // significance filter
    const key = path + '::' + name;
    const meta = funcMeta[key];
    if (!meta) {
      console.error('MISSING func meta: ' + key);
      continue;
    }
    nodes.push({
      id: 'function:' + path + ':' + name,
      type: 'function',
      name,
      filePath: path,
      lineRange: [f.startLine, f.endLine],
      summary: meta.summary,
      tags: meta.tags,
      complexity: meta.complexity,
    });
  }
}

// ---- build edges ----
const edges = [];
for (const n of nodes) {
  if (n.type === 'file' || n.type === 'config') continue;
}
// contains edges: file node -> class/function node
for (const n of nodes) {
  if (n.type === 'class' || n.type === 'function') {
    const fid = fileNodes.get(n.filePath);
    if (!fid) { console.error('no file node for ' + n.filePath); continue; }
    edges.push({ source: fid, target: n.id, type: 'contains', direction: 'forward', weight: 1.0 });
  }
}
// exports edges: file node -> class node (exported public API)
for (const n of nodes) {
  if (n.type === 'class') {
    const fid = fileNodes.get(n.filePath);
    if (!fid) continue;
    edges.push({ source: fid, target: n.id, type: 'exports', direction: 'forward', weight: 0.8 });
  }
}
// implements edges: impl class -> interface class
for (const [implPath, ifaceName, ifacePath] of implementsPairs) {
  const src = 'class:' + implPath + ':' + implPath.split('/').pop().replace('.java', '');
  const dst = 'class:' + ifacePath + ':' + ifaceName;
  edges.push({ source: src, target: dst, type: 'implements', direction: 'forward', weight: 0.9 });
}
// configures edge: pom.xml -> spzx-user application
edges.push({
  source: 'config:spzx-modules/spzx-user/pom.xml',
  target: 'file:spzx-modules/spzx-user/src/main/java/com/spzx/user/SpzxUserApplication.java',
  type: 'configures', direction: 'forward', weight: 0.6,
});

// ---- split into parts ----
const allPaths = Object.keys(fileMeta).sort();
const parts = 2;
const partSize = Math.ceil(allPaths.length / parts);
const partOfFile = new Map();
allPaths.forEach((p, i) => partOfFile.set(p, Math.floor(i / partSize))); // 0-based part index

// determine source node's part via its filePath
function partOfNode(n) {
  if (n.type === 'file' || n.type === 'config') return partOfFile.get(n.filePath);
  return partOfFile.get(n.filePath);
}
function partOfEdge(e) {
  // edge source is a node id; find owning file
  const sourceId = e.source;
  // find node
  const node = nodes.find(n => n.id === sourceId);
  if (node) return partOfNode(node);
  // fallback: derive filePath from source node id
  return 0;
}

const partNodes = [[], []];
const partEdges = [[], []];
for (const n of nodes) {
  const p = partOfNode(n);
  partNodes[p].push(n);
}
for (const e of edges) {
  const p = partOfEdge(e);
  partEdges[p].push(e);
}

const basename = allPaths[0];
// files per part
const out = [];
for (let k = 0; k < parts; k++) {
  const frag = { nodes: partNodes[k], edges: partEdges[k] };
  out.push(frag);
}
fs.writeFileSync(root + '/.ua/intermediate/batch-35-part-1.json', JSON.stringify(out[0], null, 2));
fs.writeFileSync(root + '/.ua/intermediate/batch-35-part-2.json', JSON.stringify(out[1], null, 2));

console.log('totalNodes=' + nodes.length + ' totalEdges=' + edges.length);
console.log('part1 nodes=' + out[0].nodes.length + ' edges=' + out[0].edges.length);
console.log('part2 nodes=' + out[1].nodes.length + ' edges=' + out[1].edges.length);
console.log('part1 files=' + partNodes[0].filter(n => n.type === 'file' || n.type === 'config').length);
console.log('part2 files=' + partNodes[1].filter(n => n.type === 'file' || n.type === 'config').length);
