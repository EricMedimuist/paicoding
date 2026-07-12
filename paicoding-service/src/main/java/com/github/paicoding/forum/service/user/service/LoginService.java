package com.github.paicoding.forum.service.user.service;

import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.UserZsxqLoginReq;
import com.github.paicoding.forum.service.user.service.help.UserSessionHelper;

/**
 * @author YiHui
 * @date 2022/8/15
 */
public interface LoginService {
    String SESSION_KEY = "f-session";
    String USER_DEVICE_KEY = "f-device";


    /**
     * 适用于微信公众号登录场景下，自动注册一个用户
     *
     * @param uuid 微信唯一标识
     * @return userId 用户主键
     */
    Long autoRegisterWxUserInfo(String uuid);

    /**
     * <p><strong>业务功能：</strong></p>
     * 注销指定的用户会话。
     *
     * <p><strong>执行流程：</strong></p>
     * 服务实现会使该会话失效并记录下线信息；会话为空时直接结束。
     *
     * @param session 待注销的用户会话标识
     */
    void logout(String session);

    /**
     * 给微信公众号的用户生成一个用于登录的会话
     *
     * @param userId 用户主键id
     * @return
     */
    String loginByWx(Long userId);

    String loginByWx(Long userId, UserSessionHelper.SessionDeviceMeta sessionMeta);

    /**
     * <p><strong>业务功能：</strong></p>
     * 使用用户名和密码认证用户并创建登录会话。
     *
     * <p><strong>执行流程：</strong></p>
     * 实现会查询用户、校验密码、初始化必要的用户扩展信息，认证成功后更新当前请求上下文并生成会话。
     * 用户不存在、密码错误或账号不允许登录时通过业务异常中断。
     *
     * @param username 登录用户名
     * @param password 待校验的明文密码
     * @return 认证成功后生成的登录会话标识
     */
    String loginByUserPwd(String username, String password);

    /**
     * <p><strong>业务功能：</strong></p>
     * 使用用户名和密码注册独立账号，并为新用户创建登录会话。
     *
     * <p><strong>执行流程：</strong></p>
     * 实现会校验注册参数和当前登录状态，检查用户名唯一性，创建用户及关联资料，
     * 最后更新请求上下文并生成会话。参数非法或用户名重复时通过业务异常中断。
     *
     * @param loginReq 注册信息，主要包含用户名和密码
     * @return 注册成功后生成的登录会话标识
     */
    String registerByUsernameAndPassword(UserPwdLoginReq loginReq);

    /**
     * <p><strong>业务功能：</strong></p>
     * 未登录用户使用已有账号登录，并同时绑定知识星球信息。
     *
     * <p><strong>执行流程：</strong></p>
     * 实现会校验未登录前提、账号密码和星球绑定信息，更新用户的星球资料，
     * 然后写入当前请求上下文并创建登录会话。任一业务校验失败时通过业务异常中断。
     *
     * @param loginReq 登录及绑定信息，包含用户名、密码、星球编号和可选邀请码
     * @return 登录并绑定成功后生成的登录会话标识
     */
    String loginAndBindingByUserPwd(UserPwdLoginReq loginReq);

    /**
     * <p><strong>业务功能：</strong></p>
     * 为当前已登录用户绑定用户名、密码和知识星球信息。
     *
     * <p><strong>执行流程：</strong></p>
     * 实现会从请求上下文取得当前用户，校验账号及星球绑定信息并更新用户资料；
     * 不生成新的登录会话。未登录或绑定校验失败时通过业务异常中断。
     *
     * @param loginReq 待绑定的用户名、密码、星球编号和可选邀请码
     * @return 完成绑定的当前用户 ID
     */
    Long bindingCurrentUserByUserPwd(UserPwdLoginReq loginReq);


    /**
     * <p><strong>业务功能：</strong></p>
     * 根据知识星球授权信息完成登录或账号初始化。
     *
     * <p><strong>执行流程：</strong></p>
     * 首次授权时创建本站用户和星球资料；已有绑定时同步星球有效期及状态。
     * 完成用户状态处理后更新请求上下文并创建登录会话，业务处理失败时通过异常中断。
     *
     * @param req 已通过授权回调校验的知识星球用户信息
     * @return 知识星球登录成功后生成的登录会话标识
     */
    String loginByZsxq(UserZsxqLoginReq req);
}
