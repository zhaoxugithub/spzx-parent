import fs from 'fs';
import path from 'path';

const SEC = "spzx-common/spzx-common-security/src/main/java/com/spzx/common/security";
const SECRES = "spzx-common/spzx-common-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
const GW = "spzx-gateway/src/main/java/com/spzx/gateway";

const files = [
  {path: SEC+"/annotation/EnableRyFeignClients.java", type:"file", name:"EnableRyFeignClients.java", summary:"自定义 Feign 启动注解，封装 @EnableFeignClients 并默认扫描 com.spzx 包，用于开启微服务间的 Feign 调用。", tags:["annotation","feign","microservice","spring","config"], complexity:"simple"},
  {path: SEC+"/annotation/InnerAuth.java", type:"file", name:"InnerAuth.java", summary:"内部认证注解，标记仅供内部服务间调用的接口方法，由 InnerAuthAspect 校验内部信任头。", tags:["annotation","security","auth","feign","spring"], complexity:"simple"},
  {path: SEC+"/annotation/Logical.java", type:"file", name:"Logical.java", summary:"认证逻辑枚举，定义 AND/OR 两种多权限或多角色的组合校验模式，被权限注解引用。", tags:["enum","security","auth","type-definition","rbac"], complexity:"simple"},
  {path: SEC+"/annotation/RequiresLogin.java", type:"file", name:"RequiresLogin.java", summary:"登录认证注解，标注方法或类需登录后才能访问，交由 PreAuthorizeAspect 统一处理。", tags:["annotation","security","auth","login","spring"], complexity:"simple"},
  {path: SEC+"/annotation/RequiresPermissions.java", type:"file", name:"RequiresPermissions.java", summary:"权限认证注解，声明访问所需权限码及 AND/OR 校验模式，用于方法级 RBAC 鉴权。", tags:["annotation","security","auth","permission","rbac"], complexity:"simple"},
  {path: SEC+"/annotation/RequiresRoles.java", type:"file", name:"RequiresRoles.java", summary:"角色认证注解，声明访问所需角色标识及 AND/OR 校验模式，用于方法级 RBAC 鉴权。", tags:["annotation","security","auth","role","rbac"], complexity:"simple"},
  {path: SEC+"/aspect/InnerAuthAspect.java", type:"file", name:"InnerAuthAspect.java", summary:"内部认证切面，拦截 @InnerAuth 注解方法，校验请求来源头以阻止外部伪造内部调用。", tags:["aspect","security","auth","aop","spring"], complexity:"moderate"},
  {path: SEC+"/aspect/PreAuthorizeAspect.java", type:"file", name:"PreAuthorizeAspect.java", summary:"预授权切面，基于 @RequiresLogin/@RequiresPermissions/@RequiresRoles 注解执行方法级权限校验。", tags:["aspect","security","auth","aop","rbac"], complexity:"moderate"},
  {path: SEC+"/auth/AuthLogic.java", type:"file", name:"AuthLogic.java", summary:"认证与授权核心逻辑类，实现登录校验、用户角色/权限获取与注解驱动的鉴权判断。", tags:["security","auth","rbac","service","authorization"], complexity:"complex"},
  {path: SEC+"/auth/AuthUtil.java", type:"file", name:"AuthUtil.java", summary:"认证工具门面，将 AuthLogic 能力包装为静态方法，方便调用方完成登录与鉴权判断。", tags:["security","auth","utility","facade","authorization"], complexity:"moderate"},
  {path: SEC+"/config/ApplicationConfig.java", type:"file", name:"ApplicationConfig.java", summary:"应用配置类，注册 Jackson 时区序列化定制与 TraceId 过滤器 Bean。", tags:["config","spring","serialization","trace-id","web"], complexity:"moderate"},
  {path: SEC+"/config/WebMvcConfig.java", type:"file", name:"WebMvcConfig.java", summary:"Web MVC 配置，注册 HeaderInterceptor 拦截器并配置放行路径，用于注入用户上下文。", tags:["config","spring","web-mvc","interceptor","security"], complexity:"moderate"},
  {path: SEC+"/feign/FeignAutoConfiguration.java", type:"file", name:"FeignAutoConfiguration.java", summary:"Feign 自动配置类，提供 FeignRequestInterceptor Bean 以在跨服务调用中传播用户上下文。", tags:["config","feign","spring","microservice","security"], complexity:"simple"},
  {path: SEC+"/feign/FeignRequestInterceptor.java", type:"file", name:"FeignRequestInterceptor.java", summary:"Feign 请求拦截器，将请求头中的用户上下文（userId、username、用户key 等）转发到下游服务。", tags:["feign","interceptor","spring","microservice","context"], complexity:"moderate"},
  {path: SEC+"/filter/TraceIdFilter.java", type:"file", name:"TraceIdFilter.java", summary:"链路追踪过滤器，从请求头读取 traceId 写入 MDC，实现全链路日志追踪。", tags:["filter","logging","trace-id","web","spring"], complexity:"moderate"},
  {path: SEC+"/handler/GlobalExceptionHandler.java", type:"file", name:"GlobalExceptionHandler.java", summary:"全局异常处理器，集中捕获鉴权、参数校验及运行时异常并转换为统一响应结构。", tags:["exception-handler","security","web","validation","spring"], complexity:"complex"},
  {path: SEC+"/interceptor/HeaderInterceptor.java", type:"file", name:"HeaderInterceptor.java", summary:"请求头拦截器，从请求头还原用户信息到 SecurityContextHolder，并自动刷新会话过期时间。", tags:["interceptor","security","context","web","spring"], complexity:"moderate"},
  {path: SEC+"/service/TokenService.java", type:"file", name:"TokenService.java", summary:"登录令牌服务，负责 JWT 创建解析、登录用户存取校验与自动续签。", tags:["token","security","jwt","redis","service"], complexity:"complex"},
  {path: SEC+"/utils/DictUtils.java", type:"file", name:"DictUtils.java", summary:"字典缓存工具类，封装字典数据的 Redis 缓存读写、删除与清理。", tags:["utility","dictionary","redis","cache","security"], complexity:"moderate"},
  {path: SEC+"/utils/SecurityUtils.java", type:"file", name:"SecurityUtils.java", summary:"安全工具类，提供当前用户信息、令牌解析、管理员判断与 BCrypt 密码校验等便捷方法。", tags:["utility","security","auth","password","context"], complexity:"moderate"},
  {path: SECRES, type:"file", name:"org.springframework.boot.autoconfigure.AutoConfiguration.imports", summary:"Spring Boot 自动配置注册清单，声明 security 模块中的自动配置类。", tags:["spring","auto-configuration","config","module-registry"], complexity:"simple", languageNotes:"Spring Boot 3 使用 AutoConfiguration.imports 注册自动配置类。"},
  {path: "spzx-gateway/pom.xml", type:"config", name:"pom.xml", summary:"spzx-gateway 网关模块的 Maven 构建配置，声明 Gateway、Nacos、Sentinel、验证码及公共 Redis 等依赖。", tags:["configuration","maven","build-system","gateway","dependency-management"], complexity:"moderate", languageNotes:"spring-boot-maven-plugin 通过 repackage 生成可执行 jar。"},
  {path: GW+"/SpzxGatewayApplication.java", type:"file", name:"SpzxGatewayApplication.java", summary:"网关服务启动类，使用 @SpringBootApplication 启动 Spring Cloud Gateway 网关应用。", tags:["entry-point","spring","gateway","application","boot"], complexity:"simple"},
  {path: GW+"/config/CaptchaConfig.java", type:"file", name:"CaptchaConfig.java", summary:"验证码配置类，定义普通字符与数学运算两种 Kaptcha 验证码 Bean。", tags:["config","captcha","kaptcha","gateway","bean"], complexity:"complex"},
  {path: GW+"/config/GatewayConfig.java", type:"file", name:"GatewayConfig.java", summary:"网关配置类，注册 Sentinel 网关异常降级处理器 Bean。", tags:["config","gateway","sentinel","fallback","spring"], complexity:"simple"},
];

const classes = [
  {path: SEC+"/annotation/EnableRyFeignClients.java", name:"EnableRyFeignClients", lineRange:[16,27], summary:"Feign 启动注解接口，默认扫描 com.spzx 包以启用服务间 Feign 客户端。", tags:["annotation","feign","microservice","spring"], complexity:"simple"},
  {path: SEC+"/annotation/InnerAuth.java", name:"InnerAuth", lineRange:[13,19], summary:"内部认证注解，通过 isUser 决定是否校验用户信息。", tags:["annotation","security","auth","spring"], complexity:"simple"},
  {path: SEC+"/annotation/Logical.java", name:"Logical", lineRange:[9,20], summary:"认证组合逻辑枚举，提供 AND/OR 两种校验模式。", tags:["enum","security","auth","rbac"], complexity:"simple"},
  {path: SEC+"/annotation/RequiresLogin.java", name:"RequiresLogin", lineRange:[16,18], summary:"登录认证注解，限定方法或类需登录后才能访问。", tags:["annotation","security","auth","login"], complexity:"simple"},
  {path: SEC+"/annotation/RequiresPermissions.java", name:"RequiresPermissions", lineRange:[16,26], summary:"权限认证注解，声明所需权限码与 AND/OR 校验逻辑。", tags:["annotation","security","auth","permission"], complexity:"simple"},
  {path: SEC+"/annotation/RequiresRoles.java", name:"RequiresRoles", lineRange:[15,26], summary:"角色认证注解，声明所需角色标识与 AND/OR 校验逻辑。", tags:["annotation","security","auth","role"], complexity:"simple"},
  {path: SEC+"/aspect/InnerAuthAspect.java", name:"InnerAuthAspect", lineRange:[19,47], summary:"内部认证切面类，环绕拦截 @InnerAuth 注解方法并校验内部来源头。", tags:["aspect","security","auth","aop"], complexity:"moderate"},
  {path: SEC+"/aspect/PreAuthorizeAspect.java", name:"PreAuthorizeAspect", lineRange:[22,90], summary:"预授权切面类，通过 AOP 在方法调用前执行登录、角色与权限校验。", tags:["aspect","security","auth","aop","rbac"], complexity:"moderate"},
  {path: SEC+"/auth/AuthLogic.java", name:"AuthLogic", lineRange:[27,326], summary:"鉴权核心类，封装登录校验、令牌管理、角色与权限匹配及注解鉴权逻辑。", tags:["security","auth","rbac","service","authorization"], complexity:"complex"},
  {path: SEC+"/auth/AuthUtil.java", name:"AuthUtil", lineRange:[12,151], summary:"鉴权工具门面类，以静态方法暴露 AuthLogic 的登录与鉴权能力。", tags:["security","auth","utility","facade"], complexity:"moderate"},
  {path: SEC+"/config/ApplicationConfig.java", name:"ApplicationConfig", lineRange:[15,36], summary:"应用配置类，配置 Jackson 时区并注册 TraceId 过滤器。", tags:["config","spring","serialization","trace-id"], complexity:"moderate"},
  {path: SEC+"/config/WebMvcConfig.java", name:"WebMvcConfig", lineRange:[12,29], summary:"Web MVC 配置类，注册 HeaderInterceptor 拦截器并配置放行路径。", tags:["config","spring","web-mvc","interceptor"], complexity:"moderate"},
  {path: SEC+"/feign/FeignAutoConfiguration.java", name:"FeignAutoConfiguration", lineRange:[12,18], summary:"Feign 自动配置类，注册 Feign 上下文传播拦截器 Bean。", tags:["config","feign","spring","microservice"], complexity:"simple"},
  {path: SEC+"/feign/FeignRequestInterceptor.java", name:"FeignRequestInterceptor", lineRange:[19,54], summary:"Feign 请求拦截器，将请求头中的用户上下文与来源信息传播到下游服务。", tags:["feign","interceptor","spring","context"], complexity:"moderate"},
  {path: SEC+"/filter/TraceIdFilter.java", name:"TraceIdFilter", lineRange:[26,53], summary:"过滤器类，将 traceId 写入 MDC 并在请求结束时清理。", tags:["filter","logging","trace-id","web"], complexity:"moderate"},
  {path: SEC+"/handler/GlobalExceptionHandler.java", name:"GlobalExceptionHandler", lineRange:[27,159], summary:"全局异常处理器，将各类异常转换为统一错误响应。", tags:["exception-handler","security","web","validation"], complexity:"complex"},
  {path: SEC+"/interceptor/HeaderInterceptor.java", name:"HeaderInterceptor", lineRange:[21,49], summary:"请求头拦截器，还原用户信息到上下文并在会话过期前续期。", tags:["interceptor","security","context","web"], complexity:"moderate"},
  {path: SEC+"/service/TokenService.java", name:"TokenService", lineRange:[28,158], summary:"令牌服务，负责 JWT 生成解析、登录用户存取与自动续签。", tags:["token","security","jwt","redis","service"], complexity:"complex"},
  {path: SEC+"/utils/DictUtils.java", name:"DictUtils", lineRange:[17,75], summary:"字典缓存工具类，封装字典数据的 Redis 缓存操作。", tags:["utility","dictionary","redis","cache"], complexity:"moderate"},
  {path: SEC+"/utils/SecurityUtils.java", name:"SecurityUtils", lineRange:[17,105], summary:"安全工具类，提供用户信息、令牌、管理员判断与密码校验能力。", tags:["utility","security","auth","password"], complexity:"moderate"},
  {path: GW+"/SpzxGatewayApplication.java", name:"SpzxGatewayApplication", lineRange:[12,27], summary:"网关启动类，启动 Spring Cloud Gateway 应用。", tags:["entry-point","spring","gateway","application"], complexity:"simple"},
  {path: GW+"/config/CaptchaConfig.java", name:"CaptchaConfig", lineRange:[17,82], summary:"验证码配置类，提供普通字符与数学运算两种 Kaptcha Bean。", tags:["config","captcha","kaptcha","gateway"], complexity:"complex"},
  {path: GW+"/config/GatewayConfig.java", name:"GatewayConfig", lineRange:[14,21], summary:"网关配置类，注册 Sentinel 网关降级处理器。", tags:["config","gateway","sentinel","fallback"], complexity:"simple"},
];

const funcs = [
  {path: SEC+"/aspect/InnerAuthAspect.java", name:"innerAround", lineRange:[22,37], summary:"环绕通知，校验请求头 from-source 是否为内部来源，阻止外部伪造内部调用。", tags:["aspect","security","auth","validation"], complexity:"simple"},
  {path: SEC+"/aspect/PreAuthorizeAspect.java", name:"around", lineRange:[53,66], summary:"环绕通知，解析目标方法上的鉴权注解并执行校验。", tags:["aspect","security","auth","aop"], complexity:"simple"},
  {path: SEC+"/aspect/PreAuthorizeAspect.java", name:"checkMethodAnnotation", lineRange:[71,89], summary:"反射读取方法上的登录、角色、权限注解并调用对应校验逻辑。", tags:["security","auth","reflection","validation"], complexity:"simple"},
  {path: SEC+"/auth/AuthLogic.java", name:"getLoginUser", lineRange:[70,80], summary:"从令牌获取并校验当前登录用户，未登录时抛出异常。", tags:["security","auth","token","context"], complexity:"simple"},
  {path: SEC+"/auth/AuthLogic.java", name:"checkPermi", lineRange:[128,137], summary:"按 @RequiresPermissions 注解的 AND/OR 模式校验权限码。", tags:["security","auth","rbac","permission"], complexity:"simple"},
  {path: SEC+"/auth/AuthLogic.java", name:"checkPermiAnd", lineRange:[144,153], summary:"校验当前用户是否同时具备权限注解声明的所有权限。", tags:["security","auth","rbac","permission"], complexity:"simple"},
  {path: SEC+"/auth/AuthLogic.java", name:"checkPermiOr", lineRange:[160,170], summary:"校验当前用户是否具备权限注解声明的任一权限。", tags:["security","auth","rbac","permission"], complexity:"simple"},
  {path: SEC+"/auth/AuthLogic.java", name:"checkRoleAnd", lineRange:[211,220], summary:"校验当前用户是否同时具备角色注解声明的所有角色。", tags:["security","auth","rbac","role"], complexity:"simple"},
  {path: SEC+"/auth/AuthLogic.java", name:"checkRoleOr", lineRange:[227,238], summary:"校验当前用户是否具备角色注解声明的任一角色。", tags:["security","auth","rbac","role"], complexity:"simple"},
  {path: SEC+"/feign/FeignRequestInterceptor.java", name:"apply", lineRange:[21,53], summary:"为 Feign 请求模板注入用户上下文与来源等请求头，实现上下文跨服务传播。", tags:["feign","interceptor","context","spring"], complexity:"moderate"},
  {path: SEC+"/filter/TraceIdFilter.java", name:"doFilter", lineRange:[34,48], summary:"读取 traceId 写入 MDC，执行后续过滤器链并清理上下文。", tags:["filter","logging","trace-id","web"], complexity:"simple"},
  {path: SEC+"/service/TokenService.java", name:"createToken", lineRange:[48,69], summary:"基于 LoginUser 生成 JWT 令牌并写入 Redis 会话缓存。", tags:["token","security","jwt","redis"], complexity:"moderate"},
  {path: SEC+"/service/TokenService.java", name:"getLoginUser", lineRange:[96,108], summary:"解析令牌从 Redis 读取登录用户，捕获并记录异常。", tags:["token","security","jwt","context"], complexity:"simple"},
  {path: SEC+"/utils/DictUtils.java", name:"getDictCache", lineRange:[36,44], summary:"按缓存 key 读取字典数据并转换为列表返回。", tags:["utility","dictionary","redis","cache"], complexity:"simple"},
  {path: GW+"/SpzxGatewayApplication.java", name:"main", lineRange:[14,26], summary:"网关应用入口，启动 Spring Cloud Gateway 并打印启动信息。", tags:["entry-point","spring","gateway","boot"], complexity:"simple"},
  {path: GW+"/config/CaptchaConfig.java", name:"getKaptchaBean", lineRange:[19,44], summary:"构建普通字符验证码 Kaptcha Bean，配置字体、尺寸与干扰线等属性。", tags:["config","captcha","kaptcha","bean"], complexity:"moderate"},
  {path: GW+"/config/CaptchaConfig.java", name:"getKaptchaBeanMath", lineRange:[46,81], summary:"构建数学运算验证码 Kaptcha Bean，配置运算文本生成器及相关属性。", tags:["config","captcha","kaptcha","bean"], complexity:"moderate"},
];

// sorted file paths (alphabetical) and part assignment
const sortedPaths = files.map(f=>f.path).sort();
const parts = 2;
const chunk = Math.ceil(sortedPaths.length / parts);
const partOf = (p) => sortedPaths.indexOf(p) < chunk ? 1 : 2;

const nodes = [];
const edges = [];
const nodeIdSet = new Set();

function addNode(node){ if(nodeIdSet.has(node.id)){ throw new Error("dup node "+node.id); } nodeIdSet.add(node.id); nodes.push(node); }

for (const f of files){
  const n = {id: f.type+":"+f.path, type: f.type, name: f.name, filePath: f.path, summary: f.summary, tags: f.tags, complexity: f.complexity};
  if (f.languageNotes) n.languageNotes = f.languageNotes;
  addNode(n);
}
for (const c of classes){
  const id = "class:"+c.path+":"+c.name;
  addNode({id, type:"class", name:c.name, filePath:c.path, lineRange:c.lineRange, summary:c.summary, tags:c.tags, complexity:c.complexity});
  edges.push({source:"file:"+c.path, target:id, type:"contains", direction:"forward", weight:1.0});
  edges.push({source:"file:"+c.path, target:id, type:"exports", direction:"forward", weight:0.8});
}
for (const fn of funcs){
  const id = "function:"+fn.path+":"+fn.name;
  addNode({id, type:"function", name:fn.name, filePath:fn.path, lineRange:fn.lineRange, summary:fn.summary, tags:fn.tags, complexity:fn.complexity});
  edges.push({source:"file:"+fn.path, target:id, type:"contains", direction:"forward", weight:1.0});
  edges.push({source:"file:"+fn.path, target:id, type:"exports", direction:"forward", weight:0.8});
}

console.log("total nodes:", nodes.length, "edges:", edges.length, "importsCount=0");
console.log("nodeCount>60?", nodes.length>60, "edgeCount>120?", edges.length>120);

const outdir = "C:/Users/11931/IdeaProjects/spzx-parent/.ua/intermediate";
for (let k=1;k<=parts;k++){
  const partNodes = nodes.filter(n=> n.filePath && partOf(n.filePath)===k);
  const partNodeIds = new Set(partNodes.map(n=>n.id));
  const partEdges = edges.filter(e=> partNodeIds.has(e.source));
  const frag = { nodes: partNodes, edges: partEdges };
  const fname = "batch-25-part-"+k+".json";
  fs.writeFileSync(path.join(outdir, fname), JSON.stringify(frag, null, 2), "utf8");
  console.log("wrote", fname, "nodes="+partNodes.length, "edges="+partEdges.length);
}
