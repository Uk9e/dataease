package io.dataease.community.permissions.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("core_rbac_role")
public class CommunityRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Boolean systemRole;
    private Long oid;
    private Long createTime;
}
