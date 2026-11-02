package io.dataease.community.permissions.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.constant.AuthConstant;
import io.dataease.exception.DEException;
import io.dataease.result.ResultMessage;
import io.dataease.utils.AuthUtils;
import io.dataease.utils.JsonUtil;
import io.dataease.utils.ServletUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CommunityRbacSessionFilter implements Filter {

    private final CommunityRbacService rbacService;

    public CommunityRbacSessionFilter(CommunityRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (StringUtils.isNotBlank(ServletUtils.getHead(AuthConstant.LINK_TOKEN_KEY))) {
            chain.doFilter(request, response);
            return;
        }
        String token = ServletUtils.getToken();
        TokenUserBO tokenUser = AuthUtils.getUser();
        if (StringUtils.isBlank(token) || tokenUser == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            DecodedJWT jwt = JWT.decode(token);
            rbacService.validateSession(tokenUser, jwt.getClaim("authVersion").asLong());
            chain.doFilter(request, response);
        } catch (DEException exception) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
            httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.setHeader("DE-GATEWAY-FLAG", "permission-changed");
            ResultMessage result = new ResultMessage(exception.getCode(), exception.getMessage());
            httpResponse.getWriter().write(JsonUtil.toJSONString(result).toString());
        }
    }
}
