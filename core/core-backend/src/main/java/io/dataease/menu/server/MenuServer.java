package io.dataease.menu.server;

import io.dataease.api.menu.MenuApi;
import io.dataease.api.menu.vo.MenuVO;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.community.permissions.service.CommunityRbacService;
import io.dataease.menu.dao.auto.entity.CoreMenu;
import io.dataease.menu.manage.MenuManage;
import io.dataease.utils.AuthUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/menu")
public class MenuServer implements MenuApi {

    @Resource
    private MenuManage menuManage;

    @Resource
    private ObjectProvider<CommunityRbacService> rbacServiceProvider;

    @Override
    public List<MenuVO> query() {
        List<CoreMenu> coreMenus = menuManage.coreMenus();
        CommunityRbacService rbacService = rbacServiceProvider.getIfAvailable();
        TokenUserBO user = AuthUtils.getUser();
        if (rbacService != null && user != null && !rbacService.isAdmin(user.getUserId(), user.getDefaultOid())) {
            coreMenus = filterAuthorizedMenus(coreMenus,
                    rbacService.authorizedMenuIds(user.getUserId(), user.getDefaultOid()));
        }
        return menuManage.query(new ArrayList<>(coreMenus));
    }

    private List<CoreMenu> filterAuthorizedMenus(List<CoreMenu> menus, Set<Long> authorizedIds) {
        Map<Long, CoreMenu> byId = new HashMap<>();
        menus.forEach(menu -> byId.put(menu.getId(), menu));
        Set<Long> includedIds = new HashSet<>(authorizedIds);
        if (authorizedIds.contains(5L)) {
            includedIds.add(11L);
        }
        if (authorizedIds.contains(6L)) {
            includedIds.add(12L);
        }
        for (Long id : new ArrayList<>(includedIds)) {
            CoreMenu current = byId.get(id);
            while (current != null && current.getPid() != null && current.getPid() != 0L) {
                includedIds.add(current.getPid());
                current = byId.get(current.getPid());
            }
        }
        return menus.stream().filter(menu -> includedIds.contains(menu.getId())).toList();
    }
}
