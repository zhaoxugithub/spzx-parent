package com.spzx.auth.service;

import com.spzx.common.core.constant.CacheConstants;
import com.spzx.common.core.constant.Constants;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.constant.UserConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.enums.UserStatus;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.core.text.Convert;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.common.core.utils.ip.IpUtils;
import com.spzx.common.redis.service.RedisService;
import com.spzx.common.security.utils.SecurityUtils;
import com.spzx.system.api.RemoteUserService;
import com.spzx.system.api.domain.SysUser;
import com.spzx.system.api.model.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 登录校验方法
 *
 * @author spzx
 */
@Component
public class SysLoginService {
    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private SysPasswordService passwordService;

    @Autowired
    private SysRecordLogService recordLogService;

    @Autowired
    private RedisService redisService;

    /**
     * 登录
     */
    public LoginUser login(String username, String password) {
        // 1. 参数校验：用户名/密码不能为空
        if (StringUtils.isAnyBlank(username, password)) {
            loginFail(username, "用户/密码必须填写");
        }
        // 2. 参数校验：密码长度必须在指定范围内
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH) {
            loginFail(username, "用户密码不在指定范围");
        }
        // 3. 参数校验：用户名长度必须在指定范围内
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH) {
            loginFail(username, "用户名不在指定范围");
        }
        // 4. IP 黑名单校验
        String blackStr = Convert.toStr(redisService.getCacheObject(CacheConstants.SYS_LOGIN_BLACKIPLIST));
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr())) {
            loginFail(username, "很遗憾，访问IP已被列入系统黑名单");
        }
        // 5. 远程查询用户信息（Feign 调用 spzx-system 的 RemoteUserService）
        R<LoginUser> userResult = remoteUserService.getUserInfo(username, SecurityConstants.INNER);

        // 5.1 远程服务调用异常/降级：先判断 R.FAIL，避免降级返回被误判为"用户不存在"
        if (StringUtils.isNull(userResult)) {
            loginFail(username, "登录服务调用失败");
        }
        if (R.FAIL == userResult.getCode()) {
            loginFail(username, userResult.getMsg());
        }
        // 5.2 用户不存在
        if (StringUtils.isNull(userResult.getData())) {
            loginFail(username, "登录用户：" + username + " 不存在");
        }

        LoginUser userInfo = userResult.getData();
        SysUser user = userInfo.getSysUser();
        // 6. 账号状态校验：已删除
        if (UserStatus.DELETED.getCode().equals(user.getDelFlag())) {
            loginFail(username, "对不起，您的账号：" + username + " 已被删除");
        }
        // 7. 账号状态校验：已停用
        if (UserStatus.DISABLE.getCode().equals(user.getStatus())) {
            loginFail(username, "对不起，您的账号：" + username + " 已停用");
        }
        // 8. 密码校验（内含错误次数限制：连续 5 次错误锁定 10 分钟）
        passwordService.validate(userInfo, password);
        // 9. 记录登录成功日志
        recordLogService.recordLogininfor(username, Constants.LOGIN_SUCCESS, "登录成功");
        return userInfo;
    }

    /**
     * 登出
     */
    public void logout(String loginName) {
        recordLogService.recordLogininfor(loginName, Constants.LOGOUT, "退出成功");
    }

    /**
     * 注册
     */
    public void register(String username, String password) {
        // 用户名或密码为空 错误
        if (StringUtils.isAnyBlank(username, password)) {
            throw new ServiceException("用户/密码必须填写");
        }
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH) {
            throw new ServiceException("账户长度必须在2到20个字符之间");
        }
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH) {
            throw new ServiceException("密码长度必须在5到20个字符之间");
        }

        // 注册用户信息
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);
        sysUser.setNickName(username);
        sysUser.setPassword(SecurityUtils.encryptPassword(password));
        R<?> registerResult = remoteUserService.registerUserInfo(sysUser, SecurityConstants.INNER);

        if (R.FAIL == registerResult.getCode()) {
            throw new ServiceException(registerResult.getMsg());
        }
        recordLogService.recordLogininfor(username, Constants.REGISTER, "注册成功");
    }

    /**
     * 登录失败统一处理：记录失败日志并抛出业务异常
     *
     * @param username 用户名
     * @param message  失败原因
     */
    private void loginFail(String username, String message) {
        recordLogService.recordLogininfor(username, Constants.LOGIN_FAIL, message);
        throw new ServiceException(message);
    }
}
