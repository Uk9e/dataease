CREATE TABLE IF NOT EXISTS `core_rbac_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `account` VARCHAR(128) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `email` VARCHAR(255) DEFAULT NULL,
    `phone_prefix` VARCHAR(16) DEFAULT NULL,
    `phone` VARCHAR(32) DEFAULT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `default_oid` BIGINT NOT NULL DEFAULT 1,
    `auth_version` BIGINT NOT NULL DEFAULT 1,
    `create_time` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_core_rbac_user_account` (`account`),
    KEY `idx_core_rbac_user_oid_enabled` (`default_oid`, `enabled`)
);

CREATE TABLE IF NOT EXISTS `core_rbac_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) DEFAULT NULL,
    `system_role` TINYINT(1) NOT NULL DEFAULT 0,
    `oid` BIGINT NOT NULL DEFAULT 1,
    `create_time` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_core_rbac_role_oid_name` (`oid`, `name`)
);

CREATE TABLE IF NOT EXISTS `core_rbac_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `oid` BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_core_rbac_user_role` (`user_id`, `role_id`, `oid`),
    KEY `idx_core_rbac_user_role_role` (`role_id`, `oid`)
);

CREATE TABLE IF NOT EXISTS `core_rbac_role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    `weight` INT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_core_rbac_role_menu` (`role_id`, `menu_id`),
    KEY `idx_core_rbac_role_menu_menu` (`menu_id`)
);

INSERT INTO `core_menu` (`id`, `pid`, `type`, `name`, `component`, `menu_sort`, `icon`, `path`, `hidden`, `in_layout`, `auth`)
SELECT 91001, 15, 2, 'user', 'system/user', 20, NULL, '/user', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `core_menu` WHERE `id` = 91001);

INSERT INTO `core_menu` (`id`, `pid`, `type`, `name`, `component`, `menu_sort`, `icon`, `path`, `hidden`, `in_layout`, `auth`)
SELECT 91002, 15, 2, 'role', 'system/role', 21, NULL, '/role', 0, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `core_menu` WHERE `id` = 91002);
