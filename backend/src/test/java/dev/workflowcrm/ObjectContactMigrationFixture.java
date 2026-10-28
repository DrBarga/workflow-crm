package dev.workflowcrm;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class ObjectContactMigrationFixture {
    final JdbcTemplate jdbc;
    final UUID region = UUID.randomUUID(), manager = UUID.randomUUID(), object = UUID.randomUUID();

    ObjectContactMigrationFixture(Connection connection) {
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    void seed() {
        jdbc.update("insert into regions(id,code,name) values (?,?,'Migration region')", region, region.toString());
        boolean legacy = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from information_schema.columns where table_name='users' and column_name='region_id')",
                Boolean.class));
        if (legacy) {
            jdbc.update(
                    "insert into users(id,email,password_hash,full_name,role_id,region_id,status) "
                            + "select ?,?,'test-hash','Migration manager',id,?,'ACTIVE' from roles where code='MANAGER'",
                    manager,
                    manager + "@example.com",
                    region);
        } else {
            jdbc.update(
                    "insert into users(id,email,password_hash,full_name,role_id,status) select ?,?,'test-hash','Migration manager',id,'ACTIVE' from roles where code='MANAGER'",
                    manager,
                    manager + "@example.com");
            UserRegionTestData.assign(jdbc, manager, region);
        }
        createObject(object, true);
        request(object);
    }

    void createObject(UUID id, boolean contact) {
        jdbc.update(
                "insert into objects(id,name,region_id,manager_id,author_id,status_id,entrance_longitude,entrance_latitude,object_area,contact_name,contact_role,contact_phone) "
                        + "select ?,'Migration object',?,?,?,id,30.5,50.4,100,?,?,? from object_statuses where code='FOUND'",
                id,
                region,
                manager,
                manager,
                contact ? "Owner" : null,
                contact ? "Owner" : null,
                contact ? "+380671234567" : null);
    }

    void request(UUID id) {
        jdbc.update(
                "insert into object_creation_requests(actor_id,request_key,request_hash,object_id) values (?,?,?,?)",
                manager,
                id.toString(),
                "a".repeat(64),
                id);
    }

    String objectSnapshot(UUID id) {
        return jdbc.queryForObject("select to_jsonb(o)::text from objects o where id=?", String.class, id);
    }

    List<String> schema() {
        var columns = jdbc.queryForList(
                "select table_name||':'||column_name||':'||data_type||':'||is_nullable||':'||coalesce(column_default,'') "
                        + "from information_schema.columns where table_schema='public' order by table_name,ordinal_position",
                String.class);
        var result = new ArrayList<>(columns);
        result.addAll(jdbc.queryForList(
                "select c.relname||':'||con.conname||':'||pg_get_constraintdef(con.oid) from pg_constraint con "
                        + "join pg_class c on c.oid=con.conrelid join pg_namespace n on n.oid=c.relnamespace "
                        + "where n.nspname='public' order by c.relname,con.conname",
                String.class));
        result.addAll(jdbc.queryForList(
                "select tablename||':'||indexname||':'||indexdef from pg_indexes "
                        + "where schemaname='public' order by tablename,indexname",
                String.class));
        return result;
    }
}
