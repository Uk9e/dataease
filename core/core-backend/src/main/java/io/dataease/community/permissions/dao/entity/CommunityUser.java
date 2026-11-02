package io.dataease.community.permissions.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("core_rbac_user")
public class CommunityUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String account;
    private String passwordHash;
    private String name;
    private String email;
    private String phonePrefix;
    private String phone;
    private Boolean enabled;
    private Long defaultOid;
    private Long authVersion;
    private Long createTime;
}
