package com.github.paicoding.forum.web.front.login.pwd;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.api.model.vo.user.UserPwdLoginReq;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.core.util.SessionUtil;
import com.github.paicoding.forum.service.user.service.LoginService;
import com.github.paicoding.forum.service.user.service.audit.UserShareRiskControlService;
import com.github.paicoding.forum.web.front.login.zsxq.helper.ZsxqHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * 用户名 密码方式的登录/登出的入口
 *
 * @author YiHui
 * @date 2022/8/15
 */
@RestController
@RequestMapping
public class LoginRestController {
    @Autowired
    private LoginService loginService;
    @Autowired
    private ZsxqHelper zsxqHelper;
    @Autowired
    private UserShareRiskControlService userShareRiskControlService;

    /**
     * <p><strong>业务功能：</strong></p>
     * 使用用户名和密码登录，并在登录成功后建立浏览器登录态。
     *
     * <p><strong>执行流程：</strong></p>
     * <ol>
     *     <li>调用登录服务查询用户、校验密码并生成会话；</li>
     *     <li>将会话写入 Cookie，供后续请求识别用户身份；</li>
     *     <li>查询本次登录的风险提示并随成功结果返回。</li>
     * </ol>
     *
     * @param username 登录用户名
     * @param password 登录密码
     * @param response HTTP 响应，用于写入登录 Cookie
     * @return 登录成功返回 {@code true}；未生成会话时返回登录失败结果
     */
    @PostMapping("/login/username")
    public ResVo<Boolean> login(@RequestParam(name = "username") String username,
                                @RequestParam(name = "password") String password,
                                HttpServletResponse response) {
        // 查询用户并校验密码；认证成功后生成当前用户的登录会话
        String session = loginService.loginByUserPwd(username, password);
        if (StringUtils.isNotBlank(session)) {
            // 将会话写入 Cookie，供浏览器后续请求携带并识别登录身份
            response.addCookie(SessionUtil.newCookie(LoginService.SESSION_KEY, session));
            ResVo<Boolean> vo = ResVo.ok(true);

            // 登录成功后查询风控提示；提示只增强响应信息，不改变成功状态
            String riskTip = userShareRiskControlService.getHighRiskLoginTip(ReqInfoContext.getReqInfo().getUserId());
            if (StringUtils.isNotBlank(riskTip)) {
                vo.getStatus().setMsg(riskTip);
            }
            return vo;
        } else {
            // 用户不存在、密码错误等常规失败会由 Service 抛出异常；此处处理未生成会话的兜底情况
            return ResVo.fail(StatusEnum.LOGIN_FAILED_MIXED, "用户名和密码登录异常，请稍后重试");
        }
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 使用用户名和密码注册独立账号，并在注册成功后自动登录。
     *
     * <p><strong>执行流程：</strong></p>
     * <ol>
     *     <li>调用登录服务校验注册参数并创建用户；</li>
     *     <li>为新用户生成登录会话；</li>
     *     <li>将会话写入浏览器 Cookie；</li>
     *     <li>从当前请求上下文取得新用户 ID 并返回。</li>
     * </ol>
     *
     * @param loginReq 注册信息，主要包含用户名和密码
     * @param response HTTP 响应，用于写入登录 Cookie
     * @return 注册成功时返回新用户 ID；未生成会话时返回注册失败结果
     */
    @PostMapping("/register/username")
    public ResVo<Long> registerByUsername(UserPwdLoginReq loginReq, HttpServletResponse response) {
        // 校验注册信息、创建用户，并为新用户生成登录会话
        String session = loginService.registerByUsernameAndPassword(loginReq);
        if (StringUtils.isNotBlank(session)) {
            // 注册成功即自动登录，将会话写入 Cookie，供后续请求识别用户身份
            response.addCookie(SessionUtil.newCookie(LoginService.SESSION_KEY, session));

            // 登录服务已将新用户写入当前请求上下文，可直接取得用户 ID
            Long userId = ReqInfoContext.getReqInfo().getUserId();
            return ResVo.ok(userId);
        } else {
            // 正常业务校验失败通常由 Service 抛出异常；这里处理未生成会话的兜底情况
            return ResVo.fail(StatusEnum.LOGIN_FAILED_MIXED, "register failed, please try again later");
        }
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 未登录用户使用已有用户名和密码登录，同时为该账号绑定知识星球信息。
     *
     * <p><strong>执行流程：</strong></p>
     * <p>验证当前请求未登录，校验账号密码和星球绑定信息，更新用户的星球资料，
     * 生成登录会话并写入 Cookie，最后返回登录用户 ID。</p>
     *
     * @param loginReq 登录及绑定信息，包含用户名、密码、星球编号和可选邀请码
     * @param response HTTP 响应，用于写入登录 Cookie
     * @return 登录并绑定成功时返回用户 ID；未生成会话时返回失败结果
     */
    @PostMapping("/binding/login")
    public ResVo<Long> loginAndBinding(UserPwdLoginReq loginReq, HttpServletResponse response) {
        // 验证已有账号并完成星球绑定，成功后为用户生成新的登录会话
        String session = loginService.loginAndBindingByUserPwd(loginReq);
        if (StringUtils.isNotBlank(session)) {
            // 将新会话写入 Cookie，使未登录用户在绑定完成后进入登录状态
            response.addCookie(SessionUtil.newCookie(LoginService.SESSION_KEY, session));

            // Service 已将认证成功的用户写入请求上下文
            return ResVo.ok(ReqInfoContext.getReqInfo().getUserId());
        }

        // 常规校验失败由 Service 抛出异常；此处处理未生成会话的兜底情况
        return ResVo.fail(StatusEnum.LOGIN_FAILED_MIXED, "login and binding failed, please try again later");
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 为当前已登录用户绑定用户名、密码和知识星球信息。
     *
     * <p><strong>执行流程：</strong></p>
     * <p>接口先通过 {@link Permission} 校验登录态，再由登录服务取得当前用户、校验绑定信息，
     * 并更新该用户的账号及星球资料。该流程沿用当前会话，不创建新的登录会话。</p>
     *
     * @param loginReq 待绑定的用户名、密码、星球编号和可选邀请码
     * @return 绑定成功时返回当前用户 ID
     */
    @Permission(role = UserRole.LOGIN)
    @PostMapping("/binding/account")
    public ResVo<Long> bindingCurrentAccount(UserPwdLoginReq loginReq) {
        // 登录态已由权限拦截器检查，Service 负责二次校验并更新当前账号的绑定信息
        return ResVo.ok(loginService.bindingCurrentUserByUserPwd(loginReq));
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 注销当前登录用户，并清理浏览器和服务端的登录状态。
     *
     * <p><strong>执行流程：</strong></p>
     * <p>使 HttpSession 失效，注销当前业务会话，删除登录 Cookie，
     * 最后重定向回请求来源页面；没有来源页面时返回站点首页。</p>
     *
     * @param request 当前 HTTP 请求，用于取得会话和来源页面
     * @param response HTTP 响应，用于执行页面重定向
     * @return 注销流程完成后返回成功结果
     * @throws IOException 重定向响应写入失败
     */
    @Permission(role = UserRole.LOGIN)
    @RequestMapping("logout")
    public ResVo<Boolean> logOut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 先释放 Servlet 容器维护的 HttpSession
        request.getSession().invalidate();

        // 从当前请求上下文取得业务会话，并通知登录服务使其失效
        Optional.ofNullable(ReqInfoContext.getReqInfo()).ifPresent(s -> loginService.logout(s.getSession()));

        // 删除浏览器中的登录 Cookie，避免后续请求继续携带旧会话
        SessionUtil.delCookies(LoginService.SESSION_KEY);

        // 优先返回用户发起退出操作的页面；缺少 Referer 时回到首页
        String referer = request.getHeader("Referer");
        if (StringUtils.isBlank(referer)) {
            referer = "/";
        }
        response.sendRedirect(referer);
        return ResVo.ok(true);
    }

    /**
     * <p><strong>业务功能：</strong></p>
     * 跳转到知识星球授权登录页面。
     *
     * <p><strong>执行流程：</strong></p>
     * <p>本接口只根据登录场景构造第三方授权地址并执行重定向，不在此处完成用户认证；
     * 用户授权后的回调流程会校验星球身份并建立本站登录态。</p>
     *
     * @param response HTTP 响应，用于重定向到知识星球授权地址
     * @throws IOException 重定向响应写入失败
     */
    @RequestMapping("login/zsxq")
    public void redirectToZsxq(HttpServletResponse response) throws IOException {
        // 构造“登录”场景对应的知识星球授权地址
        String url = zsxqHelper.buildZsxqLoginUrl("login");

        // 将浏览器引导至第三方授权页面，真正的登录在授权回调中完成
        response.sendRedirect(url);
    }
}
