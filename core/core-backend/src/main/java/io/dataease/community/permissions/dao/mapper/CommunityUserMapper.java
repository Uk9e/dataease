package io.dataease.community.permissions.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.dataease.community.permissions.dao.entity.CommunityUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommunityUserMapper extends BaseMapper<CommunityUser> {

    @Select("""
            SELECT u.id
            FROM core_rbac_user u
            JOIN core_rbac_user_role ur ON ur.user_id = u.id AND ur.oid = u.default_oid
            JOIN core_rbac_role r ON r.id = ur.role_id AND r.oid = ur.oid
            WHERE u.default_oid = #{oid} AND u.enabled = 1 AND r.system_role = 1
            ORDER BY u.id
            FOR UPDATE
            """)
    List<Long> lockEnabledAdminIds(@Param("oid") Long oid);
}
