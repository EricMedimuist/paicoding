package com.github.paicoding.forum.service.user.service.user;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.enums.user.LoginTypeEnum;
import com.github.paicoding.forum.api.model.enums.user.UserAIStatEnum;
import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.api.model.vo.user.UserSaveReq;
import com.github.paicoding.forum.api.model.vo.user.UserZsxqLoginReq;
import com.github.paicoding.forum.core.util.StarNumberUtil;
import com.github.paicoding.forum.service.image.service.ImageService;
import com.github.paicoding.forum.service.user.repository.dao.UserAiDao;
import com.github.paicoding.forum.service.user.repository.dao.UserDao;
import com.github.paicoding.forum.service.user.repository.entity.UserAiDO;
import com.github.paicoding.forum.service.user.repository.entity.UserDO;
import com.github.paicoding.forum.service.user.service.LoginService;
import com.github.paicoding.forum.service.user.service.LoginAuditService;
import com.github.paicoding.forum.service.user.service.RegisterService;
import com.github.paicoding.forum.service.user.service.UserAiService;
import com.github.paicoding.forum.service.user.service.UserService;
import com.github.paicoding.forum.service.user.service.audit.UserShareRiskControlService;
import com.github.paicoding.forum.service.user.service.help.StarNumberHelper;
import com.github.paicoding.forum.service.user.service.help.UserPwdEncoder;
import com.github.paicoding.forum.service.user.service.help.UserSessionHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.Objects;

/**
 * 基于验证码、用户名密码的登录方式
 *
 * @author YiHui
 * @date 2022/8/15
 */
@Service
@Slf4j
public class LoginServiceImpl implements LoginService {
    private static final int MIN_REGISTER_PASSWORD_LENGTH = 8;
    private static final int MAX_REGISTER_PASSWORD_LENGTH = 64;

    @Autowired
    private UserDao userDao;

    @Autowired
    private UserAiDao userAiDao;

    @Autowired
    private UserSessionHelper userSessionHelper;
    @Autowired
    private StarNumberHelper starNumberHelper;

    @Autowired
    private RegisterService registerService;

    @Autowired
    private UserPwdEncoder userPwdEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private UserAiService userAiService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private LoginAuditService loginAuditService;
    @Autowired
    private UserShareRiskControlService userShareRiskControlService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long autoRegisterWxUserInfo(String uuid) {
        UserSaveReq req = new UserSaveReq().setLoginType(0).setThirdAccountId(uuid);
        Long userId = registerOrGetUserInfo(req);
        ReqInfoContext.getReqInfo().setUserId(userId);
        return userId;
    }

    /**
     * 没有注册时，先注册一个用户；若已经有，则登录
     *
     * @param req
     */
    private Long registerOrGetUserInfo(UserSaveReq req) {
        UserDO user = userDao.getByThirdAccountId(req.getThirdAccountId());
        if (user == null) {
            return registerService.registerByWechat(req.getThirdAccountId());
        }
        return user.getId();
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 注销指定的业务会话。
     *
     * <p><strong>执行流程：</strong></p>
     * 将会话失效及下线记录统一委托给会话组件处理。
     *
     * @param session 待注销的用户会话标识
     */
    @Override
    public void logout(String session) {
        // 会话组件负责移除会话及其关联状态，并记录用户主动退出
        userSessionHelper.logout(session);
    }

    /**
     * 给微信公众号的用户生成一个用于登录的会话
     *
     * @param userId 用户id
     * @return
     */
    @Override
    public String loginByWx(Long userId) {
        return userSessionHelper.genSession(userId, null, LoginTypeEnum.WECHAT.getType());
    }

    @Override
    public String loginByWx(Long userId, UserSessionHelper.SessionDeviceMeta sessionMeta) {
        return userSessionHelper.genSession(userId, null, LoginTypeEnum.WECHAT.getType(), sessionMeta);
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 使用用户名和密码完成用户认证，并在认证成功后创建登录会话。
     *
     * <p><strong>执行流程：</strong></p>
     * 先查询用户并校验密码，失败时记录登录审计并抛出对应业务异常；成功后补齐用户 AI 信息，
     * 将用户 ID 写入当前请求上下文，最后生成并返回登录会话。
     *
     * @param username 登录用户名
     * @param password 待校验的明文密码
     * @return 认证成功后生成的登录会话标识
     */
    @Override
    public String loginByUserPwd(String username, String password) {
        // 根据登录名查询账号，用户不存在时记录失败原因并终止认证
        UserDO user = userDao.getUserByUserName(username);
        if (user == null) {
            loginAuditService.recordLoginFail(username, LoginTypeEnum.USER_PWD.getType(), "用户不存在", buildCurrentSessionMeta());
            throw ExceptionUtil.of(StatusEnum.USER_NOT_EXISTS, "userName=" + username);
        }

        // 使用密码编码器比对明文密码和数据库密文，避免在业务层处理加密细节
        if (!userPwdEncoder.match(password, user.getPassword())) {
            UserSessionHelper.SessionDeviceMeta sessionMeta = buildCurrentSessionMeta();
            sessionMeta.setUserId(user.getId());
            loginAuditService.recordLoginFail(username, LoginTypeEnum.USER_PWD.getType(), "密码错误", sessionMeta);
            throw ExceptionUtil.of(StatusEnum.USER_PWD_ERROR);
        }

        Long userId = user.getId();
        // 为兼容历史账号数据，在首次成功登录时初始化或补齐用户 AI 信息
        userAiService.initOrUpdateAiInfo(new UserPwdLoginReq().setUserId(userId).setUsername(username).setPassword(password));

        // 在生成会话前更新请求上下文，供 Controller 和本次请求的后续逻辑取得当前用户
        ReqInfoContext.getReqInfo().setUserId(userId);

        // 生成用户名密码登录类型的会话并返回给 Controller
        return userSessionHelper.genSession(userId, username, LoginTypeEnum.USER_PWD.getType());
    }


    /**
     * <p><strong>业务功能：</strong></p>
     * 注册仅使用用户名和密码的独立账号，并在注册成功后自动建立登录会话。
     *
     * <p><strong>执行流程：</strong></p>
     * 依次校验注册参数、禁止已登录用户重复注册、检查用户名唯一性，随后将请求字段归一化为
     * 普通账号注册所需的数据并创建用户。创建成功后更新请求上下文并生成登录会话。
     *
     * @param loginReq 注册信息，主要包含用户名和密码
     * @return 注册成功后生成的登录会话标识
     */
    @Override
    public String registerByUsernameAndPassword(UserPwdLoginReq loginReq) {
        // 校验用户名格式、密码长度及必要参数，非法输入通过业务异常终止注册
        registerUserPwdOnlyPreCheck(loginReq);

        // 独立账号注册只允许未登录用户调用，避免将注册行为误用为当前账号绑定
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        if (reqInfo != null && reqInfo.getUserId() != null) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "already logged in");
        }

        // 去除用户名首尾空白并检查唯一性，防止重复创建登录账号
        String username = loginReq.getUsername().trim();
        if (userDao.getUserByUserName(username) != null) {
            throw ExceptionUtil.of(StatusEnum.USER_LOGIN_NAME_REPEAT, username);
        }

        // 普通注册不接收星球、邀请码和第三方账号字段，由服务端统一注册类型及显示名
        loginReq.setUsername(username)
                .setDisplayName(username)
                .setLoginType(LoginTypeEnum.USER_PWD.getType())
                .setStarNumber(null)
                .setInvitationCode(null)
                .setThirdAccountId(null);

        // 创建用户、基础资料和默认 AI 信息，并取得新用户 ID
        Long userId = registerService.registerByUserNameAndPassword(loginReq);
        if (reqInfo != null) {
            // 将新用户写入当前请求上下文，供 Controller 返回用户 ID
            reqInfo.setUserId(userId);
        }

        // 注册成功即自动登录，为新账号生成用户名密码类型的会话
        return userSessionHelper.genSession(userId, username, LoginTypeEnum.USER_PWD.getType());
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 未登录用户使用已有用户名密码登录，并在同一流程中绑定知识星球信息。
     *
     * <p><strong>执行流程：</strong></p>
     * 先验证未登录前提和账号密码，再检查星球编号及邀请码是否可绑定；校验通过后更新用户的
     * AI/星球资料，将用户写入请求上下文并生成登录会话。
     *
     * @param loginReq 登录及绑定信息，包含用户名、密码、星球编号和可选邀请码
     * @return 登录并绑定成功后生成的登录会话标识
     */
    @Override
    public String loginAndBindingByUserPwd(UserPwdLoginReq loginReq) {
        // 该入口只服务未登录用户，已登录用户应使用当前账号绑定接口
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        if (reqInfo != null && reqInfo.getUserId() != null) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "already logged in");
        }

        // 登录前先检查账号密码是否齐全
        if (loginReq == null || StringUtils.isBlank(loginReq.getUsername()) || StringUtils.isBlank(loginReq.getPassword())) {
            throw ExceptionUtil.of(StatusEnum.USER_PWD_ERROR);
        }

        // 查询已有账号并验证密码，只有认证通过的用户才能修改绑定信息
        UserDO user = userDao.getUserByUserName(loginReq.getUsername());
        if (user == null) {
            throw ExceptionUtil.of(StatusEnum.USER_NOT_EXISTS, loginReq.getUsername());
        }
        if (!userPwdEncoder.match(loginReq.getPassword(), user.getPassword())) {
            throw ExceptionUtil.of(StatusEnum.USER_PWD_ERROR);
        }

        Long userId = user.getId();

        // 校验星球编号、重复绑定和邀请码，确保星球信息可绑定到该用户
        bindingPreCheck(loginReq, userId);
        loginReq.setUserId(userId);

        // 将通过校验的星球信息写入当前用户的 AI 扩展资料
        userAiService.initOrUpdateAiInfo(loginReq);
        if (reqInfo != null) {
            // 认证完成后更新请求上下文，供 Controller 返回当前用户 ID
            reqInfo.setUserId(userId);
        }

        // 未指定登录类型时按用户名密码登录处理，并创建新的登录会话
        Integer loginType = loginReq.getLoginType() == null ? LoginTypeEnum.USER_PWD.getType() : loginReq.getLoginType();
        return userSessionHelper.genSession(userId, loginReq.getUsername(), loginType);
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 为当前已登录用户绑定用户名、密码及知识星球信息。
     *
     * <p><strong>执行流程：</strong></p>
     * 从请求上下文确认并取得当前用户，校验绑定信息后更新用户账号及 AI/星球资料。
     * 此方法沿用现有登录态，不生成新的会话。
     *
     * @param loginReq 待绑定的用户名、密码、星球编号和可选邀请码
     * @return 完成绑定的当前用户 ID
     */
    @Override
    public Long bindingCurrentUserByUserPwd(UserPwdLoginReq loginReq) {
        // Service 层再次校验登录态，避免仅依赖 Web 层权限拦截
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        if (reqInfo == null || reqInfo.getUserId() == null) {
            throw ExceptionUtil.of(StatusEnum.FORBID_NOTLOGIN);
        }

        // 绑定目标固定为请求上下文中的当前用户，客户端不能自行指定用户 ID
        Long userId = reqInfo.getUserId();

        // 校验星球编号、重复绑定和邀请码后，将目标用户 ID 写入绑定请求
        bindingPreCheck(loginReq, userId);
        loginReq.setUserId(userId);

        // 更新当前用户的用户名、加密密码及 AI/星球绑定信息
        userService.bindUserInfo(loginReq);
        return userId;
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 校验独立用户名密码注册所需的基础参数。
     *
     * <p><strong>执行流程：</strong></p>
     * 用户名和密码必须存在；用户名仅允许 4-32 位字母、数字、下划线或连字符，
     * 密码长度必须在 8-64 位之间。校验失败时抛出参数业务异常。
     *
     * @param loginReq 待校验的注册信息
     */
    private void registerUserPwdOnlyPreCheck(UserPwdLoginReq loginReq) {
        // 必要字段为空时不再继续执行格式校验
        if (loginReq == null || StringUtils.isBlank(loginReq.getUsername()) || StringUtils.isBlank(loginReq.getPassword())) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "username and password cannot be blank");
        }

        // 用户名去除首尾空白后，只允许系统支持的字符和长度范围
        String username = loginReq.getUsername().trim();
        if (!username.matches("[A-Za-z0-9_-]{4,32}")) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "username only supports 4-32 letters, numbers, underscores or hyphens");
        }

        // 限制原始密码长度，避免过短密码和异常超长输入
        int passwordLength = loginReq.getPassword().length();
        if (passwordLength < MIN_REGISTER_PASSWORD_LENGTH || passwordLength > MAX_REGISTER_PASSWORD_LENGTH) {
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "password length must be 8-64");
        }
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 校验知识星球信息能否绑定到指定用户。
     *
     * <p><strong>执行流程：</strong></p>
     * 校验账号密码字段、规范化并验证星球编号，阻止星球编号被其他用户重复绑定，
     * 并在提供邀请码时检查邀请码是否存在。已绑定给目标用户的相同星球编号允许重复提交。
     *
     * @param loginReq 待校验的登录及星球绑定信息
     * @param bindingUserId 本次绑定的目标用户 ID
     */
    private void bindingPreCheck(UserPwdLoginReq loginReq, Long bindingUserId) {
        // 绑定流程要求同时提供用户名和密码，缺少任一字段均视为账号校验失败
        if (loginReq == null || StringUtils.isBlank(loginReq.getUsername()) || StringUtils.isBlank(loginReq.getPassword())) {
            throw ExceptionUtil.of(StatusEnum.USER_PWD_ERROR);
        }

        String starNumber = loginReq.getStarNumber();
        // 星球编号为必填项；存在时先统一格式再进行有效性校验
        if (StringUtils.isNotBlank(starNumber)) {
            starNumber = StarNumberUtil.formatStarNumber(starNumber);
            loginReq.setStarNumber(starNumber);

            if (Boolean.FALSE.equals(starNumberHelper.checkStarNumber(starNumber))) {
                throw ExceptionUtil.of(StatusEnum.USER_STAR_NOT_EXISTS, "星球编号=" + starNumber);
            }
        } else {
            throw ExceptionUtil.of(StatusEnum.USER_STAR_EMPTY);
        }

        // 星球编号已被占用时，仅允许原绑定用户重复提交，禁止绑定到其他账号
        UserAiDO userAi = userAiDao.getByStarNumber(loginReq.getStarNumber());
        if (userAi != null) {
            if (bindingUserId != null && userAi.getUserId().equals(bindingUserId)) {
                return;
            }
            
            throw ExceptionUtil.of(StatusEnum.USER_STAR_REPEAT, loginReq.getStarNumber());
        }

        // 邀请码为可选字段；一旦填写，就必须能够找到对应的邀请用户
        String invitationCode = loginReq.getInvitationCode();
        if (StringUtils.isNotBlank(invitationCode) && userAiDao.getByInviteCode(invitationCode) == null) {
            throw ExceptionUtil.of(StatusEnum.UNEXPECT_ERROR, "非法的邀请码【" + starNumber + "】");
        }
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 根据知识星球授权回调信息完成本站登录或首次账号初始化。
     *
     * <p><strong>执行流程：</strong></p>
     * 先按星球编号查找绑定关系：首次授权时创建本站用户并同步星球资料，已有绑定时更新
     * 星球有效期和账号状态。处理完成后更新请求上下文，安排授权后的风控解禁动作并生成登录会话。
     *
     * @param req 已通过授权回调校验的知识星球用户信息
     * @return 知识星球登录成功后生成的登录会话标识
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String loginByZsxq(UserZsxqLoginReq req) {
        Long userId;

        // 以星球编号查询现有绑定关系，据此区分首次授权和已有用户
        UserAiDO aiDO = userAiDao.getByStarNumber(req.getStarNumber());
        if (aiDO == null) {
            // 首次授权：将星球资料转换为本站注册信息，并创建用户及关联资料
            UserPwdLoginReq loginReq = new UserPwdLoginReq()
                    // 星球编号
                    .setStarNumber(req.getStarNumber())
                    // 使用知识星球的starNumber作为登录用户名，前缀为zsq_
                    .setUsername("zsxq_" + req.getStarNumber())
                    // 系统随机生成密码
                    .setPassword("zsxqp_" + req.getStarNumber())
                    // 使用知识星球的用户作为当前用户
                    .setDisplayName(StringUtils.isBlank(req.getDisplayName()) ? req.getUsername() : req.getDisplayName())
                    // 用户头像
                    .setAvatar(imageService.saveImg(req.getAvatar()))
                    // 过期时间
                    .setStarExpireTime(req.getExpireTime())
                    // 设置登录类型为知识星球登录
                    .setLoginType(LoginTypeEnum.ZSXQ.getType())
                    // 设置thirdAccountId为星球用户ID
                    .setThirdAccountId(String.valueOf(req.getStarUserId()));
            userId = registerService.registerByUserNameAndPassword(loginReq);

            // 授权仍在有效期内时，直接将新用户的星球状态设为正式
            if (System.currentTimeMillis() < req.getExpireTime()) {
                userAiDao.updateUserStarState(userId, UserAIStatEnum.FORMAL.getCode());
            }
        } else {
            // 已有绑定：沿用原用户，并按最新授权信息同步有效期和状态
            userId = aiDO.getUserId();
            boolean needToUpdate = false;

            // 仅在过期时间确有变化时更新，允许第三方时间值存在一秒误差
            if (aiDO.getStarExpireTime() == null ||
                    Math.abs(req.getExpireTime() - aiDO.getStarExpireTime().getTime()) > 1000) { // 允许1秒误差
                aiDO.setStarExpireTime(new Date(req.getExpireTime()));
                needToUpdate = true;
            }

            // 根据最新有效期计算正式或过期状态，避免保存与有效期不一致的账号状态
            long currentTime = System.currentTimeMillis();
            int expectedState = currentTime < req.getExpireTime() ?
                    UserAIStatEnum.FORMAL.getCode() :
                    UserAIStatEnum.EXPIRED.getCode(); // 假设有过期状态

            if (!Objects.equals(aiDO.getState(), expectedState)) {
                aiDO.setState(expectedState);
                needToUpdate = true;
            }

            // 没有字段变化时跳过数据库更新
            if (needToUpdate) {
                aiDO.setUpdateTime(new Date());
                userAiDao.updateById(aiDO);
            }
        }

        // 将最终确认的本站用户写入当前请求上下文，供后续登录流程使用
        ReqInfoContext.getReqInfo().setUserId(userId);

        // 解禁动作要写另一组表 + Redis 事件，放在事务提交后执行，避免 session 生成失败时把解禁/审计一起回滚导致状态分裂
        scheduleZsxqReleaseAfterCommit(userId);

        // 查询本站登录名，并生成知识星球登录类型的会话
        UserDO user = userDao.getUserByUserId(userId);
        return userSessionHelper.genSession(userId, user == null ? null : user.getUserName(), LoginTypeEnum.ZSXQ.getType());
    }

    private void scheduleZsxqReleaseAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            userShareRiskControlService.releaseForbiddenByZsxqAuth(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    userShareRiskControlService.releaseForbiddenByZsxqAuth(userId);
                } catch (Exception e) {
                    log.warn("releaseForbiddenByZsxqAuth after commit failed, userId={}", userId, e);
                }
            }
        });
    }

    private UserSessionHelper.SessionDeviceMeta buildCurrentSessionMeta() {
        UserSessionHelper.SessionDeviceMeta sessionMeta = new UserSessionHelper.SessionDeviceMeta();
        if (ReqInfoContext.getReqInfo() == null) {
            return sessionMeta;
        }
        sessionMeta.setDeviceId(ReqInfoContext.getReqInfo().getDeviceId());
        sessionMeta.setIp(ReqInfoContext.getReqInfo().getClientIp());
        sessionMeta.setUserAgent(ReqInfoContext.getReqInfo().getUserAgent());
        return sessionMeta;
    }
}
