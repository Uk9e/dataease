package io.dataease.utils;

import io.dataease.auth.bo.TokenUserBO;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Objects;

public class AuthUtils {

    private static final Long SYS_ADMIN_UID = 1L;

    private static final ThreadLocal<TokenUserBO> USER_INFO = new ThreadLocal<TokenUserBO>();

    public static TokenUserBO getUser() {
        if (ObjectUtils.isNotEmpty(USER_INFO.get()))
            return USER_INFO.get();
        return null;
    }

    public static void setUser(TokenUserBO userBO) {
        USER_INFO.set(userBO);
    }

    public static void remove() {
        USER_INFO.remove();
    }

    public static boolean isSysAdmin() {
        TokenUserBO user = null;
        if (ObjectUtils.isEmpty(user = getUser())) {
            return false;
        }
        Long userId = user.getUserId();
        return isSysAdmin(userId);
    }

    public static boolean isSysAdmin(Long userId) {
        TokenUserBO user = getUser();
        if (user != null && Boolean.TRUE.equals(user.getLink())) {
            return false;
        }
        if (user != null && Objects.equals(userId, user.getUserId()) && Boolean.TRUE.equals(user.getSysAdmin())) {
            return true;
        }
        return Objects.equals(userId, SYS_ADMIN_UID);
    }


}
