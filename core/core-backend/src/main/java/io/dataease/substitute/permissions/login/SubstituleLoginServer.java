package io.dataease.substitute.permissions.login;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import io.dataease.api.permissions.login.dto.PwdLoginDTO;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.auth.config.SubstituleLoginConfig;
import io.dataease.auth.vo.TokenVO;
import io.dataease.community.permissions.dao.entity.CommunityUser;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.exception.DEException;
import io.dataease.i18n.Translator;
import io.dataease.result.ResultCode;
import io.dataease.utils.AuthUtils;
import io.dataease.utils.LogUtil;
import io.dataease.utils.RsaUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@Component
@ConditionalOnMissingBean(name = "loginServer")
@RestController
@RequestMapping
public class SubstituleLoginServer {

    private final ObjectProvider<CommunityRbacService> rbacServiceProvider;

    @Value("${dataease.login_timeout:2880}")
    private long loginTimeoutMinutes;

    public SubstituleLoginServer(ObjectProvider<CommunityRbacService> rbacServiceProvider) {
        this.rbacServiceProvider = rbacServiceProvider;
    }

    @PostMapping("/login/localLogin")
    public TokenVO localLogin(@RequestBody PwdLoginDTO dto) {
        String name = decryptCredential(dto.getName());
        String password = decryptCredential(dto.getPwd());
        CommunityRbacService rbacService = rbacServiceProvider.getIfAvailable();
        if (rbacService != null) {
            CommunityUser user = rbacService.authenticate(name, password);
            return generate(new TokenUserBO(user.getId(), user.getDefaultOid()), user.getAuthVersion(),
                    SubstituleLoginConfig.getTokenSecret());
        }
        if (!StringUtils.equals("admin", name)
                || !StringUtils.equals(password, SubstituleLoginConfig.getPwd())) {
            DEException.throwException(Translator.get("i18n_login_name_pwd_err"));
        }
        return generate(new TokenUserBO(1L, 1L), 1L, SubstituleLoginConfig.getTokenSecret());
    }

    @GetMapping("/login/refresh")
    public TokenVO refresh() {
        CommunityRbacService rbacService = rbacServiceProvider.getIfAvailable();
        if (rbacService != null) {
            CommunityUser user = rbacService.currentUser();
            return generate(new TokenUserBO(user.getId(), user.getDefaultOid()), user.getAuthVersion(),
                    SubstituleLoginConfig.getTokenSecret());
        }
        TokenUserBO user = AuthUtils.getUser();
        if (user == null) {
            DEException.throwException("用户未登录");
        }
        return generate(user, 1L, SubstituleLoginConfig.getTokenSecret());
    }

    @GetMapping("/logout")
    public void logout() {
        LogUtil.info("substitule logout");
    }

    private String decryptCredential(String encryptedValue) {
        try {
            return RsaUtils.decryptStr(encryptedValue);
        } catch (RuntimeException exception) {
            DEException.throwException(ResultCode.USER_LOGIN_ERROR.code(), "登录加密密钥已更新，请刷新页面后重试");
            return null;
        }
    }

    private TokenVO generate(TokenUserBO user, Long authVersion, String secret) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        long expiresAt = System.currentTimeMillis() + Math.max(loginTimeoutMinutes, 1L) * 60_000L;
        JWTCreator.Builder builder = JWT.create()
                .withClaim("uid", user.getUserId())
                .withClaim("oid", user.getDefaultOid())
                .withClaim("authVersion", authVersion)
                .withExpiresAt(new Date(expiresAt));
        return new TokenVO(builder.sign(algorithm), expiresAt);
    }
}
