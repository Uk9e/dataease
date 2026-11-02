package io.dataease.community.permissions;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityRbacMigrationTest {

    @Test
    void desktopMigrationCreatesRbacTablesWithoutEnablingDesktopMenus() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:community_rbac;MODE=MySQL;DATABASE_TO_UPPER=FALSE", "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE core_menu (
                          id BIGINT PRIMARY KEY, pid BIGINT NOT NULL, type INT, name VARCHAR(45),
                          component VARCHAR(45), menu_sort INT, icon VARCHAR(45), path VARCHAR(45),
                          hidden TINYINT NOT NULL, in_layout TINYINT NOT NULL, auth TINYINT NOT NULL
                        )
                        """);
                String script;
                try (var stream = getClass().getResourceAsStream(
                        "/db/desktop/V2.10.25.900__community_rbac.sql")) {
                    assertThat(stream).isNotNull();
                    script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
                try (ResultSet tables = statement.executeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'core_rbac_%'")) {
                    assertThat(tables.next()).isTrue();
                    assertThat(tables.getInt(1)).isEqualTo(4);
                }
                try (ResultSet menus = statement.executeQuery(
                        "SELECT COUNT(*) FROM core_menu WHERE id IN (91001, 91002)")) {
                    assertThat(menus.next()).isTrue();
                    assertThat(menus.getInt(1)).isZero();
                }
                statement.execute("INSERT INTO core_rbac_user "
                        + "(id, account, password_hash, name, enabled, default_oid, auth_version, create_time) "
                        + "VALUES (1, 'admin', 'hash', 'Admin', 1, 1, 1, 1)");
                statement.execute("INSERT INTO core_rbac_role "
                        + "(id, name, system_role, oid, create_time) VALUES (1, 'Admin', 1, 1, 1)");
                statement.execute("INSERT INTO core_rbac_user_role (user_id, role_id, oid) VALUES (1, 1, 1)");
                try (ResultSet admins = statement.executeQuery("""
                        SELECT u.id
                        FROM core_rbac_user u
                        JOIN core_rbac_user_role ur ON ur.user_id = u.id AND ur.oid = u.default_oid
                        JOIN core_rbac_role r ON r.id = ur.role_id AND r.oid = ur.oid
                        WHERE u.default_oid = 1 AND u.enabled = 1 AND r.system_role = 1
                        ORDER BY u.id
                        FOR UPDATE
                        """)) {
                    assertThat(admins.next()).isTrue();
                    assertThat(admins.getLong(1)).isEqualTo(1L);
                }
            }
        }
    }
}
