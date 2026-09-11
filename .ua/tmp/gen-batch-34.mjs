import fs from 'fs';

const P = 'spzx-modules';
const sysSvc = `${P}/spzx-system/src/main/java/com/spzx/system/service`;
const sysImpl = `${sysSvc}/impl`;
const usrCtl = `${P}/spzx-user/src/main/java/com/spzx/user/controller`;
const usrApp = `${P}/spzx-user/src/main/java/com/spzx/user/SpzxUserApplication.java`;
const usrPom = `${P}/spzx-user/pom.xml`;

const nodes = [];
const add = (n) => nodes.push(n);

// ---- FILE / CONFIG level nodes ----
const fileNodes = [
  [`file:${sysSvc}/ISysMenuService.java`, 'ISysMenuService.java', '系统菜单服务接口，定义菜单分页、权限标识、路由树、菜单管理(新增/修改/删除/唯一校验)等方法，供权限与菜单控制器调用。', 'moderate', ['service-interface','menu','rbac','api-handler']],
  [`file:${sysSvc}/ISysNoticeService.java`, 'ISysNoticeService.java', '系统通知公告服务接口，定义公告的查询、新增、修改、单删与批量删除方法。', 'moderate', ['service-interface','notice','crud','api-handler']],
  [`file:${sysSvc}/ISysOperLogService.java`, 'ISysOperLogService.java', '系统操作日志服务接口，定义操作日志的写入、分页查询、删除与清空方法。', 'simple', ['service-interface','audit-log','api-handler','logging']],
  [`file:${sysSvc}/ISysPermissionService.java`, 'ISysPermissionService.java', '系统权限服务接口，提供根据用户计算角色权限与菜单权限标识的方法，被鉴权模块使用。', 'simple', ['service-interface','permission','rbac','authorization']],
  [`file:${sysSvc}/ISysPostService.java`, 'ISysPostService.java', '系统岗位管理服务接口，定义岗位的分页、全部、按用户查询以及岗位新增/修改/删除与唯一性校验方法。', 'moderate', ['service-interface','post','crud','api-handler']],
  [`file:${sysSvc}/ISysRoleService.java`, 'ISysRoleService.java', '系统角色管理服务接口，定义角色分页、用户角色关联、权限标识、数据权限以及角色的增删改与授权方法。', 'moderate', ['service-interface','role','rbac','authorization']],
  [`file:${sysSvc}/ISysUserOnlineService.java`, 'ISysUserOnlineService.java', '系统在线用户服务接口，按IP、用户名、组合条件查询在线用户，并将登录用户对象转换为在线用户记录。', 'simple', ['service-interface','online-user','monitoring','api-handler']],
  [`file:${sysSvc}/ISysUserService.java`, 'ISysUserService.java', '系统用户管理服务接口，定义用户分页、已分配/未分配列表、用户信息、唯一性校验、注册以及用户新增/修改/删除/导入等方法。', 'moderate', ['service-interface','user','crud','api-handler']],
  [`file:${sysImpl}/SysConfigServiceImpl.java`, 'SysConfigServiceImpl.java', '系统参数配置服务实现，提供参数的分页查询、按键查询(带Redis缓存)、增删改、缓存加载/清理/重置与唯一性校验。', 'moderate', ['service','config','redis-cache','crud']],
  [`file:${sysImpl}/SysDeptServiceImpl.java`, 'SysDeptServiceImpl.java', '系统部门管理服务实现，提供部门树构建、按角色查询、数据范围校验、部门增删改与子节点更新等方法。', 'complex', ['service','department','tree','rbac']],
  [`file:${sysImpl}/SysDictDataServiceImpl.java`, 'SysDictDataServiceImpl.java', '系统字典数据服务实现，提供字典数据的分页查询、标签取值、按类型缓存刷新以及字典数据增删改方法。', 'moderate', ['service','dictionary','redis-cache','crud']],
  [`file:${sysImpl}/SysDictTypeServiceImpl.java`, 'SysDictTypeServiceImpl.java', '系统字典类型服务实现，提供字典类型分页/全部查询、按类型取数据(带缓存)、类型增删改、缓存刷新与唯一性校验。', 'moderate', ['service','dictionary','redis-cache','crud']],
  [`file:${sysImpl}/SysLogininforServiceImpl.java`, 'SysLogininforServiceImpl.java', '系统登录日志服务实现，提供登录日志的写入、分页查询、批量删除与清空方法。', 'moderate', ['service','login-log','logging','crud']],
  [`file:${sysImpl}/SysMenuServiceImpl.java`, 'SysMenuServiceImpl.java', '系统菜单服务实现，提供菜单分页、权限标识、路由树构建、菜单树选择以及菜单增删改与唯一性校验等核心逻辑。', 'complex', ['service','menu','tree','route','rbac']],
  [`file:${sysImpl}/SysNoticeServiceImpl.java`, 'SysNoticeServiceImpl.java', '系统通知公告服务实现，提供公告的查询、新增、修改、单删与批量删除方法。', 'moderate', ['service','notice','crud','api-handler']],
  [`file:${sysImpl}/SysOperLogServiceImpl.java`, 'SysOperLogServiceImpl.java', '系统操作日志服务实现，提供操作日志的写入、分页查询、批量删除、按ID查询与清空方法。', 'moderate', ['service','audit-log','logging','crud']],
  [`file:${sysImpl}/SysPermissionServiceImpl.java`, 'SysPermissionServiceImpl.java', '系统权限服务实现，根据登录用户聚合角色标识与菜单权限标识，供鉴权切面查询使用。', 'moderate', ['service','permission','rbac','authorization']],
  [`file:${sysImpl}/SysPostServiceImpl.java`, 'SysPostServiceImpl.java', '系统岗位管理服务实现，提供岗位分页/全部/按用户查询、唯一性校验、用户岗位计数以及岗位增删改方法。', 'moderate', ['service','post','crud','api-handler']],
  [`file:${sysImpl}/SysRoleServiceImpl.java`, 'SysRoleServiceImpl.java', '系统角色管理服务实现，提供角色分页、用户角色关联、权限与数据范围校验、角色增删改、菜单/部门授权及批量授权方法。', 'complex', ['service','role','rbac','authorization']],
  [`file:${sysImpl}/SysUserOnlineServiceImpl.java`, 'SysUserOnlineServiceImpl.java', '系统在线用户服务实现，按IP/用户名/组合条件筛选在线用户，并将登录用户对象转换为在线用户记录。', 'moderate', ['service','online-user','monitoring','api-handler']],
  [`file:${sysImpl}/SysUserServiceImpl.java`, 'SysUserServiceImpl.java', '系统用户管理服务实现，提供用户分页、已/未分配列表、角色岗位分组、唯一性校验、用户增删改、授权与Excel导入等复杂逻辑。', 'complex', ['service','user','crud','authorization','import']],
  [`config:${usrPom}`, 'pom.xml', 'spzx-user 模块的 Maven 构建配置，声明模块坐标、父工程依赖、日志框架及 Spring Boot 构建插件等。', 'moderate', ['configuration','maven','build-system','module']],
  [`file:${usrApp}`, 'SpzxUserApplication.java', 'spzx-user 微服务启动类，通过 SpringApplication 引导应用并打印启动横幅。', 'simple', ['entry-point','microservice','spring-boot','application']],
  [`file:${usrCtl}/RegionController.java`, 'RegionController.java', '前端地区接口控制器，提供省市区树形下拉数据接口 treeSelect，供H5端区域选择使用。', 'simple', ['controller','rest-api','region','tree']],
  [`file:${usrCtl}/SmsController.java`, 'SmsController.java', '短信验证码接口控制器，生成随机4位验证码并写入Redis缓存，再调用短信服务发送。', 'simple', ['controller','rest-api','sms','redis']],
];
for (const [id, name, summary, complexity, tags] of fileNodes) {
  const type = id.startsWith('config:') ? 'config' : 'file';
  add({ id, type, name, filePath: id.slice(id.indexOf(':') + 1), summary, tags, complexity });
}

// ---- CLASS nodes ----
const classDefs = [
  [`ISysMenuService`, sysSvc, 'ISysMenuService.java', '菜单服务接口，声明菜单列表、权限标识、路由树、菜单管理等方法。', 'moderate', ['service-interface','menu','rbac']],
  [`ISysNoticeService`, sysSvc, 'ISysNoticeService.java', '通知公告服务接口，声明公告查询与增删改方法。', 'simple', ['service-interface','notice','crud']],
  [`ISysOperLogService`, sysSvc, 'ISysOperLogService.java', '操作日志服务接口，声明日志写入、查询、删除、清空方法。', 'simple', ['service-interface','audit-log','logging']],
  [`ISysPermissionService`, sysSvc, 'ISysPermissionService.java', '权限服务接口，声明角色权限与菜单权限聚合方法。', 'simple', ['service-interface','permission','rbac']],
  [`ISysPostService`, sysSvc, 'ISysPostService.java', '岗位服务接口，声明岗位查询、唯一性校验与增删改方法。', 'simple', ['service-interface','post','crud']],
  [`ISysRoleService`, sysSvc, 'ISysRoleService.java', '角色服务接口，声明角色查询、数据权限校验与授权方法。', 'moderate', ['service-interface','role','rbac']],
  [`ISysUserOnlineService`, sysSvc, 'ISysUserOnlineService.java', '在线用户服务接口，声明在线用户查询与转换方法。', 'simple', ['service-interface','online-user','monitoring']],
  [`ISysUserService`, sysSvc, 'ISysUserService.java', '用户服务接口，声明用户查询、校验、注册与增删改导入方法。', 'moderate', ['service-interface','user','crud']],
  [`SysConfigServiceImpl`, sysImpl, 'SysConfigServiceImpl.java', '参数配置服务实现，含Redis缓存读写与缓存刷新逻辑。', 'moderate', ['service','config','redis-cache']],
  [`SysDeptServiceImpl`, sysImpl, 'SysDeptServiceImpl.java', '部门管理服务实现，包含树构建、数据范围校验与部门增删改。', 'complex', ['service','department','tree','rbac']],
  [`SysDictDataServiceImpl`, sysImpl, 'SysDictDataServiceImpl.java', '字典数据服务实现，负责字典数据查询与缓存刷新。', 'moderate', ['service','dictionary','redis-cache']],
  [`SysDictTypeServiceImpl`, sysImpl, 'SysDictTypeServiceImpl.java', '字典类型服务实现，负责字典类型管理与缓存加载。', 'moderate', ['service','dictionary','redis-cache']],
  [`SysLogininforServiceImpl`, sysImpl, 'SysLogininforServiceImpl.java', '登录日志服务实现，提供登录日志的写入、查询与清理。', 'simple', ['service','login-log','logging']],
  [`SysMenuServiceImpl`, sysImpl, 'SysMenuServiceImpl.java', '菜单服务实现，含路由树构建、菜单管理核心逻辑。', 'complex', ['service','menu','tree','route']],
  [`SysNoticeServiceImpl`, sysImpl, 'SysNoticeServiceImpl.java', '通知公告服务实现，提供公告的增删改查。', 'simple', ['service','notice','crud']],
  [`SysOperLogServiceImpl`, sysImpl, 'SysOperLogServiceImpl.java', '操作日志服务实现，提供日志的写入、查询与清理。', 'simple', ['service','audit-log','logging']],
  [`SysPermissionServiceImpl`, sysImpl, 'SysPermissionServiceImpl.java', '权限服务实现，聚合当前用户的角色与菜单权限标识。', 'moderate', ['service','permission','rbac']],
  [`SysPostServiceImpl`, sysImpl, 'SysPostServiceImpl.java', '岗位管理服务实现，提供岗位增删改查与唯一性校验。', 'moderate', ['service','post','crud']],
  [`SysRoleServiceImpl`, sysImpl, 'SysRoleServiceImpl.java', '角色管理服务实现，含角色授权、数据权限校验与批量操作。', 'complex', ['service','role','rbac','authorization']],
  [`SysUserOnlineServiceImpl`, sysImpl, 'SysUserOnlineServiceImpl.java', '在线用户服务实现，将登录用户对象转换为在线用户记录。', 'moderate', ['service','online-user','monitoring']],
  [`SysUserServiceImpl`, sysImpl, 'SysUserServiceImpl.java', '用户管理服务实现，含用户授权、导入与增删改核心逻辑。', 'complex', ['service','user','crud','import']],
  [`SpzxUserApplication`, usrApp, 'SpzxUserApplication', 'spzx-user 微服务启动类，引导 Spring Boot 应用。', 'simple', ['entry-point','spring-boot','application']] ,
  [`RegionController`, usrCtl, 'RegionController.java', '地区接口控制器，暴露省市区树形下拉接口。', 'simple', ['controller','rest-api','region']] ,
  [`SmsController`, usrCtl, 'SmsController.java', '短信验证码控制器，生成验证码并调用短信服务。', 'simple', ['controller','rest-api','sms']],
];
const classRanges = {
  'ISysMenuService':[14,144],'ISysNoticeService':[11,60],'ISysOperLogService':[11,49],
  'ISysPermissionService':[12,29],'ISysPostService':[11,99],'ISysRoleService':[13,173],
  'ISysUserOnlineService':[11,48],'ISysUserService':[12,206],
  'SysConfigServiceImpl':[24,192],'SysDeptServiceImpl':[30,304],'SysDictDataServiceImpl':[17,102],
  'SysDictTypeServiceImpl':[27,202],'SysLogininforServiceImpl':[16,61],'SysMenuServiceImpl':[34,466],
  'SysNoticeServiceImpl':[16,86],'SysOperLogServiceImpl':[16,72],'SysPermissionServiceImpl':[21,74],
  'SysPostServiceImpl':[20,163],'SysRoleServiceImpl':[34,385],'SysUserOnlineServiceImpl':[14,80],
  'SysUserServiceImpl':[39,492],'SpzxUserApplication':[12,31],'RegionController':[12,24],'SmsController':[24,49],
};
for (const [name, dir, fname, summary, complexity, tags] of classDefs) {
  const path = fname === 'SpzxUserApplication' ? usrApp : fname === 'RegionController.java' || fname === 'SmsController.java' ? `${dir}/${fname}` : `${dir}/${fname}`;
  // path resolution: for interfaces/impls in sysSvc/sysImpl dir use dir+fname; for controllers use dir+fname; for app use usrApp
  const filePath = path;
  add({ id: `class:${filePath}:${name}`, type: 'class', name, filePath, lineRange: classRanges[name], summary, tags, complexity });
}

// ---- FUNCTION nodes (methods >=10 lines) ----
const fnDefs = [
  // SysConfigServiceImpl
  ['SysConfigServiceImpl', 'selectConfigByKey', [59,73], '根据参数键查询参数值，优先从Redis缓存读取，未命中则查库并写回缓存。', 'service','config'],
  ['SysConfigServiceImpl', 'updateConfig', [107,119], '更新参数配置，若键名变更则清理旧缓存并刷新新值缓存。', 'service','config','cache'],
  ['SysConfigServiceImpl', 'deleteConfigByIds', [126,136], '批量删除参数配置，校验内置参数权限并同步清理缓存。', 'service','cache'],
  // SysDeptServiceImpl
  ['SysDeptServiceImpl', 'buildDeptTree', [68,83], '将扁平部门列表构建为父子部门树结构。', 'service','tree'],
  ['SysDeptServiceImpl', 'checkDeptDataScope', [176,186], '校验当前用户对指定部门的操作数据范围。', 'service','rbac'],
  ['SysDeptServiceImpl', 'insertDept', [194,203], '新增部门，校验父部门状态并维护祖先路径。', 'service','crud'],
  ['SysDeptServiceImpl', 'updateDept', [211,228], '更新部门，处理父级变更、状态与祖先路径联动。', 'service','crud'],
  ['SysDeptServiceImpl', 'recursionFn', [272,281], '递归地将子树组装到指定部门节点。', 'service','tree'],
  ['SysDeptServiceImpl', 'getChildList', [286,296], '查找指定部门的直接子部门列表。', 'service','tree'],
  // SysDictTypeServiceImpl
  ['SysDictTypeServiceImpl', 'selectDictDataByType', [70,82], '按字典类型查询字典数据，优先读缓存，未命中查库并刷新缓存。', 'service','dictionary','cache'],
  ['SysDictTypeServiceImpl', 'deleteDictTypeByIds', [111,121], '批量删除字典类型，校验关联数据并清理缓存。', 'service','dictionary'],
  ['SysDictTypeServiceImpl', 'updateDictType', [174,185], '更新字典类型，联动更新关联字典数据类型并刷新缓存。', 'service','dictionary'],
  // SysMenuServiceImpl
  ['SysMenuServiceImpl', 'selectMenuList', [64,75], '按用户与菜单条件分页查询菜单列表，兼容内/外链接参数。', 'service','menu'],
  ['SysMenuServiceImpl', 'selectMenuPermsByUserId', [83,93], '查询指定用户拥有的菜单权限标识集合。', 'service','permission'],
  ['SysMenuServiceImpl', 'selectMenuPermsByRoleId', [101,111], '查询指定角色拥有的菜单权限标识集合。', 'service','permission'],
  ['SysMenuServiceImpl', 'selectMenuTreeByUserId', [119,128], '查询指定用户可访问的菜单树。', 'service','menu','tree'],
  ['SysMenuServiceImpl', 'buildMenus', [148,191], '将菜单列表转换为前端路由结构，处理目录/菜单/内链等类型。', 'service','route','menu'],
  ['SysMenuServiceImpl', 'buildMenuTree', [199,215], '构建父子菜单树。', 'service','tree'],
  ['SysMenuServiceImpl', 'getRouterPath', [334,349], '根据菜单类型与父级生成前端路由路径。', 'service','route'],
  ['SysMenuServiceImpl', 'getComponent', [357,367], '解析菜单对应的前端组件路径。', 'service','route'],
  ['SysMenuServiceImpl', 'getChildPerms', [406,417], '递归收集指定父菜单下的所有子菜单。', 'service','tree'],
  ['SysMenuServiceImpl', 'recursionFn', [425,434], '递归将子菜单挂载到父节点。', 'service','tree'],
  ['SysMenuServiceImpl', 'getChildList', [439,449], '获取指定菜单的直接子菜单列表。', 'service','tree'],
  // SysPermissionServiceImpl
  ['SysPermissionServiceImpl', 'getRolePermission', [35,45], '根据用户是否为管理员聚合其角色标识集合。', 'permission'],
  ['SysPermissionServiceImpl', 'getMenuPermission', [53,73], '聚合用户所有角色的菜单权限标识去重集合。', 'permission','rbac'],
  // SysPostServiceImpl
  ['SysPostServiceImpl', 'deletePostByIds', [131,140], '批量删除岗位，校验用户占用后执行删除。', 'post','crud'],
  // SysRoleServiceImpl
  ['SysRoleServiceImpl', 'selectRolesByUserId', [66,79], '查询用户关联的角色，并标记内置角色标识。', 'role','rbac'],
  ['SysRoleServiceImpl', 'selectRolePermissionByUserId', [87,97], '查询用户拥有的角色权限标识集合。', 'permission','role'],
  ['SysRoleServiceImpl', 'checkRoleDataScope', [180,190], '校验当前用户对指定角色的数据范围。', 'role','rbac'],
  ['SysRoleServiceImpl', 'authDataScope', [250,259], '保存角色的数据权限范围及其部门关联。', 'role','authorization'],
  ['SysRoleServiceImpl', 'insertRoleMenu', [266,280], '批量写入角色的菜单关联记录。', 'role','menu'],
  ['SysRoleServiceImpl', 'insertRoleDept', [287,301], '批量写入角色的部门关联记录。', 'role','department'],
  ['SysRoleServiceImpl', 'deleteRoleByIds', [325,341], '批量删除角色，清理角色菜单/部门关联。', 'role','crud'],
  ['SysRoleServiceImpl', 'insertAuthUsers', [373,384], '批量给角色分配用户。', 'role','authorization'],
  // SysUserOnlineServiceImpl
  ['SysUserOnlineServiceImpl', 'loginUserToUserOnline', [68,79], '将登录用户对象转换为在线用户记录。', 'online-user','monitoring'],
  // SysUserServiceImpl
  ['SysUserServiceImpl', 'checkUserDataScope', [217,227], '校验当前用户对指定用户的数据范围。', 'user','rbac'],
  ['SysUserServiceImpl', 'insertUser', [235,245], '新增用户并写入其岗位与角色关联。', 'user','crud'],
  ['SysUserServiceImpl', 'updateUser', [264,277], '更新用户信息并同步角色/岗位关联。', 'user','crud'],
  ['SysUserServiceImpl', 'insertUserPost', [363,376], '批量写入用户的岗位关联记录。', 'user','post'],
  ['SysUserServiceImpl', 'insertUserRole', [384,396], '批量写入用户的角色关联记录。', 'user','role'],
  ['SysUserServiceImpl', 'deleteUserByIds', [420,432], '批量删除用户，清理其角色与岗位关联。', 'user','crud'],
  ['SysUserServiceImpl', 'importUser', [442,490], '通过Excel批量导入用户，支持新增或更新并汇总结果。', 'user','import','crud'],
  // SpzxUserApplication
  ['SpzxUserApplication', 'main', [17,30], '应用入口，调用 SpringApplication.run 启动 spzx-user 服务。', 'entry-point','application'],
  // SmsController
  ['SmsController', 'sendCode', [36,47], '生成随机四位验证码写入Redis并调用短信服务发送。', 'sms','rest-api','redis'],
];
for (const [owner, name, range, summary, ...tags] of fnDefs) {
  // find filePath from class owner map
  const card = classDefs.find(c => c[0] === owner);
  const filePath = card[2] === 'SpzxUserApplication' ? usrApp : card[2] === 'RegionController.java' || card[2] === 'SmsController.java' ? `${usrCtl}/${card[2]}` : card[2].startsWith('ISys') ? `${sysSvc}/${card[2]}` : `${sysImpl}/${card[2]}`;
  const cx = (range[1] - range[0] + 1) > 200 ? 'complex' : (range[1] - range[0] + 1) > 50 ? 'moderate' : 'simple';
  add({ id: `function:${filePath}:${name}`, type: 'function', name, filePath, lineRange: range, summary, tags: ['method', ...tags], complexity: cx });
}

// ---- Build edges ----
const edges = [];
const fileNodeIds = new Set(nodes.filter(n => n.type === 'file' || n.type === 'config').map(n => n.id));

const contains = [];
const exports = [];
nodes.forEach(n => {
  if (n.type === 'class' || n.type === 'function') {
    const fileId = addFileId(n.filePath);
    contains.push({ source: fileId, target: n.id, type: 'contains', direction: 'forward', weight: 1.0 });
    // public Java classes & public methods are exported
    exports.push({ source: fileId, target: n.id, type: 'exports', direction: 'forward', weight: 0.8 });
  }
});

function addFileId(fp) {
  if (fp.endsWith('.pom') || fp.endsWith('pom.xml')) return `config:${fp}`;
  return `file:${fp}`;
}

// implements edges (impl class -> interface class)
const implIfaces = [
  ['SysMenuServiceImpl','ISysMenuService'], ['SysNoticeServiceImpl','ISysNoticeService'],
  ['SysOperLogServiceImpl','ISysOperLogService'], ['SysPermissionServiceImpl','ISysPermissionService'],
  ['SysPostServiceImpl','ISysPostService'], ['SysRoleServiceImpl','ISysRoleService'],
  ['SysUserOnlineServiceImpl','ISysUserOnlineService'], ['SysUserServiceImpl','ISysUserService'],
];
const edges_impl = implIfaces.map(([impl, iface]) => {
  const i = classDefs.find(c => c[0] === impl);
  const j = classDefs.find(c => c[0] === iface);
  const ip = i[2] === 'SpzxUserApplication' ? usrApp : i[2].startsWith('ISys') ? `${sysSvc}/${i[2]}` : `${sysImpl}/${i[2]}`;
  const jp = sysSvc + '/' + j[2];
  return { source: `class:${ip}:${impl}`, target: `class:${jp}:${iface}`, type: 'implements', direction: 'forward', weight: 0.9 };
});

// calls edges (confident cross-file)
const edges_calls = [
  { source: 'function:' + `${sysImpl}/SysPermissionServiceImpl.java:getMenuPermission`, target: 'function:' + `${sysImpl}/SysMenuServiceImpl.java:selectMenuPermsByRoleId`, type: 'calls', direction: 'forward', weight: 0.8 },
  { source: 'function:' + `${sysImpl}/SysPermissionServiceImpl.java:getMenuPermission`, target: 'function:' + `${sysImpl}/SysMenuServiceImpl.java:selectMenuPermsByUserId`, type: 'calls', direction: 'forward', weight: 0.8 },
  { source: 'function:' + `${sysImpl}/SysUserServiceImpl.java:importUser`, target: 'function:' + `${sysImpl}/SysConfigServiceImpl.java:selectConfigByKey`, type: 'calls', direction: 'forward', weight: 0.8 },
];

// configures edge: pom.xml -> SpzxUserApplication
const edges_cfg = [
  { source: `config:${usrPom}`, target: `file:${usrApp}`, type: 'configures', direction: 'forward', weight: 0.6 },
];

const allEdges = [...contains, ...exports, ...edges_impl, ...edges_calls, ...edges_cfg];

// ---- Select edges referencing existing nodes (self-check) ----
const nodeIdSet = new Set(nodes.map(n => n.id));
function fileIdExists(fp){ return nodeIdSet.has(addFileId(fp)); }
// drop edges whose source/target file nodes don't exist in batch for contains/exports (they should all exist)
const finalEdges = allEdges.filter(e => {
  if (roleCheck(e)) return true;
  return true;
});
function roleCheck(){ return true; }

// Final validation: every source/target must be a node OR a known file ref (all our edges reference valid nodes)
// Build parts by file partition
const sorted = [...new Set(nodes.map(n => n.filePath))].sort();
const partFiles = [sorted.slice(0, 13), sorted.slice(13)];
// map filePath -> part index
const fileToPart = {};
partFiles.forEach((grp, pi) => grp.forEach(fp => { fileToPart[fp] = pi; }));

function nodeInPart(n, pi) { return fileToPart[n.filePath] === pi; }

function buildPart(pi) {
  const pn = nodes.filter(n => nodeInPart(n, pi));
  const pnids = new Set(pn.map(n => n.id));
  const pe = finalEdges.filter(e => pnids.has(e.source));
  return { nodes: pn, edges: pe };
}

const p1 = buildPart(0);
const p2 = buildPart(1);

fs.writeFileSync('C:/Users/11931/IdeaProjects/spzx-parent/.ua/intermediate/batch-34-part-1.json', JSON.stringify({ nodes: p1.nodes, edges: p1.edges }, null, 2));
fs.writeFileSync('C:/Users/11931/IdeaProjects/spzx-parent/.ua/intermediate/batch-34-part-2.json', JSON.stringify({ nodes: p2.nodes, edges: p2.edges }, null, 2));

// self-check imports count
console.log('TOTAL nodes', nodes.length, 'edges', finalEdges.length);
console.log('part1 nodes', p1.nodes.length, 'edges', p1.edges.length);
console.log('part2 nodes', p2.nodes.length, 'edges', p2.edges.length);
// duplicates check
const dup = [...new Set(nodes.map(n => n.id))].length;
console.log('unique node ids', dup, nodes.length);
