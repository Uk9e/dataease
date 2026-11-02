CREATE TABLE IF NOT EXISTS `core_rbac_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `account` VARCHAR(128) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `email` VARCHAR(255),
    `phone_prefix` VARCHAR(16),
    `phone` VARCHAR(32),
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `default_oid` BIGINT NOT NULL DEFAULT 1,
    `auth_version` BIGINT NOT NULL DEFAULT 1,
    `create_time` BIGINT NOT NULL,
    CONSTRAINT `uk_core_rbac_user_account` UNIQUE (`account`)
);
CREATE INDEX IF NOT EXISTS `idx_core_rbac_user_oid_enabled` ON `core_rbac_user` (`default_oid`, `enabled`);

CREATE TABLE IF NOT EXISTS `core_rbac_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512),
    `system_role` TINYINT NOT NULL DEFAULT 0,
    `oid` BIGINT NOT NULL DEFAULT 1,
    `create_time` BIGINT NOT NULL,
    CONSTRAINT `uk_core_rbac_role_oid_name` UNIQUE (`oid`, `name`)
);

CREATE TABLE IF NOT EXISTS `core_rbac_user_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `oid` BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT `uk_core_rbac_user_role` UNIQUE (`user_id`, `role_id`, `oid`)
);
CREATE INDEX IF NOT EXISTS `idx_core_rbac_user_role_role` ON `core_rbac_user_role` (`role_id`, `oid`);

CREATE TABLE IF NOT EXISTS `core_rbac_role_menu` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL,
    `menu_id` BIGINT NOT NULL,
    `weight` INT NOT NULL DEFAULT 1,
    CONSTRAINT `uk_core_rbac_role_menu` UNIQUE (`role_id`, `menu_id`)
);
CREATE INDEX IF NOT EXISTS `idx_core_rbac_role_menu_menu` ON `core_rbac_role_menu` (`menu_id`);
