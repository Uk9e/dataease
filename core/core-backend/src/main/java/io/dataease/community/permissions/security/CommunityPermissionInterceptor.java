package io.dataease.community.permissions.security;

import io.dataease.auth.DeApiPath;
import io.dataease.auth.DePermit;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.constant.AuthConstant;
import io.dataease.utils.AuthUtils;
import io.dataease.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

import static io.dataease.community.permissions.CommunityRbacConstants.MANAGE_WEIGHT;
import static io.dataease.community.permissions.CommunityRbacConstants.READ_WEIGHT;

@Component
@ConditionalOnProperty(name = "dataease.community-rbac.enabled", havingValue = "true")
public class CommunityPermissionInterceptor implements HandlerInterceptor {

    private final CommunityRbacService rbacService;

    public CommunityPermissionInterceptor(CommunityRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || StringUtils.isNotBlank(ServletUtils.getHead(AuthConstant.LINK_TOKEN_KEY))
                || Boolean.TRUE.equals(request.getAttribute(AuthConstant.WHITE_LISTED_ATTRIBUTE))) {
            return true;
        }
        TokenUserBO user = AuthUtils.getUser();
        if (user == null || user.getUserId() == null) {
            throw new CommunityForbiddenException("用户未登录");
        }
        if (requiresAdministrator(request)) {
            if (!rbacService.isAdmin(user.getUserId(), user.getDefaultOid())) {
                throw new CommunityForbiddenException("当前操作仅系统管理员可执行");
            }
            user.setSysAdmin(true);
            return true;
        }
        if (rbacService.isAdmin(user.getUserId(), user.getDefaultOid())) {
            user.setSysAdmin(true);
            return true;
        }
        Class<?> handlerType = handlerMethod.getBeanType();
        ApiPermission permission = findPermission(handlerType, handlerMethod.getMethod());
        Long menuId = permission.apiPath() == null
                ? fallbackMenuId(request)
                : Long.valueOf(permission.apiPath().rt().getMenuId());
        if (menuId == null) {
            if (isMutatingMethod(handlerMethod.getMethod())
                    || (!"GET".equalsIgnoreCase(request.getMethod()) && !isReadMethod(handlerMethod.getMethod()))) {
                throw new CommunityForbiddenException("未配置权限映射的写操作默认拒绝");
            }
            return true;
        }
        int requiredWeight = requiredWeight(permission.permit(), handlerMethod.getMethod());
        boolean allowed = rbacService.hasMenuPermission(user.getUserId(), user.getDefaultOid(), menuId, requiredWeight);
        if (menuId == 2L) {
            allowed = allowed || rbacService.hasMenuPermission(user.getUserId(), user.getDefaultOid(), 3L, requiredWeight);
        }
        if (!allowed) {
            throw new CommunityForbiddenException("当前角色没有执行该功能的权限");
        }
        return true;
    }

    private Long fallbackMenuId(HttpServletRequest request) {
        String path = normalizedPath(request);
        if (path.startsWith("/dataVisualization/") || path.startsWith("/chart/")
                || path.startsWith("/chartData/")) {
            return 2L;
        }
        if (path.startsWith("/dataset")) {
            return 5L;
        }
        if (path.startsWith("/datasource/") || path.startsWith("/engine/")) {
            return 6L;
        }
        if (path.startsWith("/template/")) {
            return 31L;
        }
        if (path.startsWith("/sync/datasource/")) {
            return 23L;
        }
        if (path.startsWith("/sync/task/")) {
            return 24L;
        }
        if (path.startsWith("/sync/summary/")) {
            return 22L;
        }
        if (path.startsWith("/data-filling/")) {
            return 60L;
        }
        return null;
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        int apiPrefix = path.lastIndexOf(AuthConstant.DE_API_PREFIX);
        return apiPrefix >= 0 ? path.substring(apiPrefix + AuthConstant.DE_API_PREFIX.length()) : path;
    }

    private boolean requiresAdministrator(HttpServletRequest request) {
        String path = normalizedPath(request);
        if (path.startsWith("/role/") || path.startsWith("/auth/")) {
            return true;
        }
        if (path.startsWith("/user/")) {
            return path.startsWith("/user/pager/")
                    || path.startsWith("/user/queryById/")
                    || path.equals("/user/create")
                    || path.equals("/user/edit")
                    || path.startsWith("/user/delete/")
                    || path.equals("/user/batchDel")
                    || path.equals("/user/enable")
                    || path.startsWith("/user/role/");
        }
        return path.equals("/sysParameter/saveOnlineMap")
                || path.equals("/sysParameter/basic/query")
                || path.startsWith("/sysParameter/singleVal/")
                || path.equals("/sysParameter/basic/save")
                || (path.equals("/sysParameter/sqlbot") && "POST".equalsIgnoreCase(request.getMethod()));
    }

    private ApiPermission findPermission(Class<?> type, Method method) {
        for (Class<?> contract : type.getInterfaces()) {
            DeApiPath apiPath = AnnotatedElementUtils.findMergedAnnotation(contract, DeApiPath.class);
            if (apiPath != null) {
                DePermit permit = null;
                try {
                    Method contractMethod = contract.getMethod(method.getName(), method.getParameterTypes());
                    permit = AnnotatedElementUtils.findMergedAnnotation(contractMethod, DePermit.class);
                } catch (NoSuchMethodException ignored) {
                    // The class can implement additional endpoints outside its API contract.
                }
                return new ApiPermission(apiPath, permit);
            }
            ApiPermission nested = findPermission(contract, method);
            if (nested.apiPath() != null) {
                return nested;
            }
        }
        return new ApiPermission(null, null);
    }

    private int requiredWeight(DePermit permit, Method method) {
        if (permit != null) {
            for (String expression : permit.value()) {
                String normalized = StringUtils.lowerCase(expression);
                if (normalized.endsWith(":manage") || normalized.contains(":export")) {
                    return MANAGE_WEIGHT;
                }
            }
            return READ_WEIGHT;
        }
        return isMutatingMethod(method) ? MANAGE_WEIGHT : READ_WEIGHT;
    }

    private boolean isMutatingMethod(Method method) {
        String methodName = StringUtils.lowerCase(method.getName());
        return StringUtils.startsWithAny(methodName, "create", "save", "update", "edit", "delete", "remove",
                "move", "copy", "batch", "enable", "reset", "mount", "unmount", "publish", "upload",
                "execute", "start", "stop", "clear", "switch", "import", "revert", "alter", "change");
    }

    private boolean isReadMethod(Method method) {
        String methodName = StringUtils.lowerCase(method.getName());
        return StringUtils.startsWithAny(methodName, "query", "get", "list", "find", "search", "page", "info",
                "count", "check", "validate", "preview", "detail", "option", "selected", "resource", "types",
                "latest", "download", "load", "calc", "default", "single", "status", "value", "source",
                "target", "is", "has");
    }

    private record ApiPermission(DeApiPath apiPath, DePermit permit) {
    }
}
