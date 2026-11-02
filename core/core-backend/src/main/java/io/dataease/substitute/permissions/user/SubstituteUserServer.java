package io.dataease.substitute.permissions.user;

import io.dataease.api.permissions.user.dto.LangSwitchRequest;
import io.dataease.api.permissions.user.dto.ModifyPwdRequest;
import io.dataease.api.permissions.user.vo.CurIpVO;
import io.dataease.api.permissions.user.vo.UserFormVO;
import io.dataease.community.permissions.dao.entity.CommunityUser;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.exception.DEException;
import io.dataease.i18n.Lang;
import io.dataease.utils.CacheUtils;
import io.dataease.utils.IPUtils;
import io.dataease.utils.RsaUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static io.dataease.constant.CacheConstant.UserCacheConstant.USER_COMMUNITY_LANGUAGE;

@Component
@ConditionalOnMissingBean(name = "userServer")
@RestController
@RequestMapping("/user")
public class SubstituteUserServer {

    private final ObjectProvider<CommunityRbacService> rbacServiceProvider;

    public SubstituteUserServer(ObjectProvider<CommunityRbacService> rbacServiceProvider) {
        this.rbacServiceProvider = rbacServiceProvider;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        CommunityUser user = currentRbacUser();
        Map<String, Object> result = new HashMap<>();
        result.put("id", user == null ? "1" : user.getId().toString());
        result.put("name", user == null ? "管理员" : user.getName());
        result.put("oid", user == null ? "1" : user.getDefaultOid().toString());
        result.put("language", currentLanguage());
        return result;
    }

    @GetMapping("/personInfo")
    public UserFormVO personInfo() {
        CommunityUser user = currentRbacUser();
        UserFormVO result = new UserFormVO();
        result.setId(user == null ? 1L : user.getId());
        result.setAccount(user == null ? "admin" : user.getAccount());
        result.setName(user == null ? "管理员" : user.getName());
        result.setEmail(user == null ? null : user.getEmail());
        result.setPhonePrefix(user == null ? null : user.getPhonePrefix());
        result.setPhone(user == null ? null : user.getPhone());
        result.setEnable(user == null || user.getEnabled());
        result.setIp(IPUtils.get());
        result.setModel("lose");
        return result;
    }

    @GetMapping("/ipInfo")
    public CurIpVO ipInfo() {
        CommunityUser user = currentRbacUser();
        CurIpVO result = new CurIpVO();
        result.setAccount(user == null ? "admin" : user.getAccount());
        result.setName(user == null ? "管理员" : user.getName());
        result.setIp(IPUtils.get());
        return result;
    }

    @PostMapping("/modifyPwd")
    public void modifyPassword(@RequestBody ModifyPwdRequest request) {
        CommunityRbacService rbacService = rbacServiceProvider.getIfAvailable();
        if (rbacService == null) {
            DEException.throwException("当前模式不支持修改密码");
        }
        rbacService.changeOwnPassword(RsaUtils.decryptStr(request.getPwd()), RsaUtils.decryptStr(request.getNewPwd()));
    }

    @PostMapping("/switchLanguage")
    public void switchLanguage(@RequestBody LangSwitchRequest request) {
        String lang = request.getLang();
        if (StringUtils.equalsIgnoreCase(Lang.zh_CN.getDesc(), lang)) {
            lang = Lang.zh_CN.getDesc();
        } else if (StringUtils.equalsAnyIgnoreCase(lang, "en", "tw")) {
            lang = lang.toLowerCase();
        } else {
            DEException.throwException("无效language");
        }
        CacheUtils.put(USER_COMMUNITY_LANGUAGE, "de", lang);
    }

    private CommunityUser currentRbacUser() {
        CommunityRbacService rbacService = rbacServiceProvider.getIfAvailable();
        return rbacService == null ? null : rbacService.currentUser();
    }

    private String currentLanguage() {
        Object lang = CacheUtils.get(USER_COMMUNITY_LANGUAGE, "de");
        return ObjectUtils.isNotEmpty(lang) && StringUtils.isNotBlank(lang.toString()) ? lang.toString() : "zh-CN";
    }
}
