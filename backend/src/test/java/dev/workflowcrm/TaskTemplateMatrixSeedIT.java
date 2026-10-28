package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import liquibase.*;
import liquibase.changelog.FastCheckService;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class TaskTemplateMatrixSeedIT {
    private static final String MASTER = "db/changelog/db.changelog-master.yaml";
    private static final String ROOT = "db/changelog/tasks/";
    private static final String CONTRACT = "58050000-0000-4000-8000-000000000010";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private Connection connection;
    private Liquibase migration;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrate() throws Exception {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (var statement = connection.createStatement()) {
            statement.execute("drop schema public cascade; create schema public");
        }
        migration = new Liquibase(MASTER, new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        update();
    }

    @AfterEach
    void close() throws Exception {
        migration.close();
    }

    @Test
    void installsOnlyApprovedPairsWithAllSevenTasksAndOneDependency() {
        assertThat(jdbc.queryForList(
                        "select s.code from task_templates t join object_statuses s on s.id=t.trigger_status_id order by s.code",
                        String.class))
                .containsExactly("CONTRACT_APPROVAL", "IC_PREPARATION");
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_templates where contract_type_id is null and contract_type is null",
                        Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForList(
                        """
                        select i.name || ':' || i.deadline_offset_days || ':' || i.assignment_rule || ':' ||
                            coalesce(a.code,'MANAGER') || ':' || i.needs_review || ':' || r.code || ':' || i.escalation_days
                        from task_template_items i join task_templates t on t.id=i.template_id
                        join object_statuses s on s.id=t.trigger_status_id
                        left join roles a on a.id=i.role_id join roles r on r.id=i.result_recipient_role_id
                        order by s.code,i.sort_order
                        """,
                        String.class))
                .containsExactly(
                        "Погодити опитувальний лист:3:REGIONAL_ROLE:REGIONAL_MANAGER:false:DEVELOPMENT_DIRECTOR:1",
                        "Погодити фінансову модель:3:REGIONAL_ROLE:REGIONAL_MANAGER:false:DEVELOPMENT_DIRECTOR:1",
                        "Погодити договір в системі Lotus:3:NETWORK_ROLE:CONTRACT_MANAGER:false:DEVELOPMENT_DIRECTOR:1",
                        "Заповнити карточку об'єкта:3:OBJECT_MANAGER:MANAGER:true:REGIONAL_MANAGER:1",
                        "Підготувати презентацію:3:OBJECT_MANAGER:MANAGER:true:REGIONAL_MANAGER:1",
                        "Отримати прорахунок ШІ:5:OBJECT_MANAGER:MANAGER:true:REGIONAL_MANAGER:1",
                        "Скласти модель:5:REGIONAL_ROLE:REGIONAL_MANAGER:false:DEVELOPMENT_DIRECTOR:1");
        assertThat(jdbc.queryForObject(
                        """
                        select i.name || ':' || p.name from task_template_item_dependencies d
                        join task_template_items i on i.id=d.item_id join task_template_items p on p.id=d.predecessor_id
                        """,
                        String.class))
                .isEqualTo("Скласти модель:Заповнити карточку об'єкта");
    }

    @Test
    void repeatedMigrationDoesNotChangeDataAndRollbackRestoresPreviousMatrix() throws Exception {
        var installed = snapshot();
        update();
        assertThat(snapshot()).isEqualTo(installed);
        migration.rollback(2, new Contexts(), new LabelExpression());
        assertThat(jdbc.queryForObject("select count(*) from task_templates", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from task_template_items", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_template_items where escalation_days is null and version=0",
                        Integer.class))
                .isEqualTo(4);
        update();
        assertThat(snapshot()).isEqualTo(installed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesExistingDefaultContractTemplateIncludingInactiveOne(boolean active) throws Exception {
        baseline();
        UUID custom = UUID.randomUUID();
        jdbc.update(
                "insert into task_templates(id,trigger_status_id,is_active) select ?,id,? from object_statuses where code='CONTRACT_APPROVAL'",
                custom,
                active);
        jdbc.update(
                "insert into task_template_items(template_id,name,deadline_offset_days,sort_order,assignment_rule) values(?,'Existing matrix',7,0,'OBJECT_MANAGER')",
                custom);
        connection.commit();
        update();
        assertThat(jdbc.queryForObject("select count(*) from task_templates where id=?", Integer.class, custom))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select is_active from task_templates where id=?", Boolean.class, custom))
                .isEqualTo(active);
        assertThat(jdbc.queryForObject(
                        "select name from task_template_items where template_id=?", String.class, custom))
                .isEqualTo("Existing matrix");
        assertThat(jdbc.queryForObject("select count(*) from task_templates where id=?::uuid", Integer.class, CONTRACT))
                .isZero();
        migration.rollback(2, new Contexts(), new LabelExpression());
        assertThat(jdbc.queryForObject("select count(*) from task_templates where id=?", Integer.class, custom))
                .isEqualTo(1);
    }

    @Test
    void typedContractTemplateDoesNotSuppressGeneralTemplate() throws Exception {
        baseline();
        jdbc.update(
                "insert into task_templates(trigger_status_id,contract_type_id,contract_type) select s.id,c.id,c.code from object_statuses s cross join contract_types c where s.code='CONTRACT_APPROVAL' and c.code='PRIMARY'");
        connection.commit();
        update();
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_templates t join object_statuses s on s.id=t.trigger_status_id where s.code='CONTRACT_APPROVAL'",
                        Integer.class))
                .isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name='Custom task'", "escalation_days=1,version=1", "escalation_days=4"})
    void doesNotRewriteModifiedIcTemplateOnUpdateOrRollback(String edit) throws Exception {
        baseline();
        jdbc.execute("update task_template_items set " + edit + " where sort_order=0");
        connection.commit();
        var before = snapshot();
        update();
        migration.rollback(2, new Contexts(), new LabelExpression());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELF", "TWO", "THREE"})
    void rejectsCyclicSeedBeforeSavingAnyTemplate(String cycle) throws Exception {
        baseline();
        var before = snapshot();
        stage();
        jdbc.execute(
                "insert into task_seed_dependencies select template_id,id,id from task_seed_items where position=0");
        if (!cycle.equals("SELF")) {
            jdbc.execute("delete from task_seed_dependencies");
            jdbc.execute(
                    "insert into task_seed_dependencies select a.template_id,a.id,b.id from task_seed_items a join task_seed_items b on b.position=1-a.position where a.position<2");
            if (cycle.equals("THREE")) {
                jdbc.execute("delete from task_seed_dependencies");
                jdbc.execute(
                        "insert into task_seed_dependencies select a.template_id,a.id,b.id from task_seed_items a join task_seed_items b on b.position=(a.position+1)%3");
            }
        }
        assertThatThrownBy(() -> execute(ROOT + "seed/template_matrix_apply.sql"))
                .hasMessageContaining("cyclic dependencies");
        connection.rollback();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL", "'EXPLICIT_USER'", "'REGIONAL_ROLE'", "'NETWORK_ROLE'", "'UNKNOWN'"})
    void rejectsTasksWithoutValidAssigneeOrRule(String rule) throws Exception {
        baseline();
        var before = snapshot();
        stage();
        jdbc.execute("update task_seed_items set assignment=" + rule + ",assignee_id=null,assignee_role=null");
        assertThatThrownBy(() -> execute(ROOT + "seed/template_matrix_apply.sql"))
                .hasMessageContaining("each task needs a valid assignee or assignment rule");
        connection.rollback();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "update task_seed_items set assignee_role='UNKNOWN'",
                "update task_seed_items set recipient_role='UNKNOWN'",
                "update task_seed_items set assignment='EXPLICIT_USER',assignee_role=null,assignee_id='00000000-0000-4000-8000-000000000099'",
                "update task_seed_templates set status_code='UNKNOWN'",
                "update task_seed_templates set contract_type_code='UNKNOWN'"
            })
    void rejectsUnknownDictionaryValuesWithoutPartialData(String edit) throws Exception {
        baseline();
        var before = snapshot();
        stage();
        jdbc.execute(edit);
        assertThatThrownBy(() -> execute(ROOT + "seed/template_matrix_apply.sql"))
                .hasMessageContaining("Task template seed: unknown");
        connection.rollback();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void sameLoaderAcceptsAnotherStatusContractPairAndMatrixWithoutJavaChanges() throws Exception {
        baseline();
        stage();
        jdbc.execute("update task_seed_templates set status_code='IN_PROGRESS',contract_type_code='PRIMARY'");
        jdbc.execute("update task_seed_items set name='Replacement task ' || position,days=9");
        execute(ROOT + "seed/template_matrix_apply.sql");
        connection.commit();
        assertThat(jdbc.queryForObject(
                        "select s.code || ':' || c.code from task_templates t join object_statuses s on s.id=t.trigger_status_id join contract_types c on c.id=t.contract_type_id",
                        String.class))
                .isEqualTo("IN_PROGRESS:PRIMARY");
        assertThat(jdbc.queryForList(
                        "select deadline_offset_days from task_template_items where template_id=?::uuid",
                        Integer.class,
                        CONTRACT))
                .containsExactly(9, 9, 9);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsMissingPredecessorOrDependencyFromAnotherTemplate(boolean anotherTemplate) throws Exception {
        baseline();
        var before = snapshot();
        stage();
        if (anotherTemplate) {
            jdbc.execute(
                    "insert into task_seed_templates(id,status_code) values('00000000-0000-4000-8000-000000000020','IN_PROGRESS')");
            jdbc.execute(
                    "insert into task_seed_items(id,template_id,name,days,position,assignment,needs_review) values('00000000-0000-4000-8000-000000000021','00000000-0000-4000-8000-000000000020','Other template task',1,0,'OBJECT_MANAGER',false)");
        }
        assertThatThrownBy(
                        () -> jdbc.execute(
                                "insert into task_seed_dependencies(template_id,item_id,predecessor_id) select template_id,id,'00000000-0000-4000-8000-000000000021'::uuid from task_seed_items where position=1"))
                .hasStackTraceContaining("foreign key constraint");
        connection.rollback();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rollbackRefusesToOverwriteIcEditsMadeAfterMigration() throws Exception {
        jdbc.execute(
                "update task_template_items set escalation_days=3,version=version+1 where sort_order=0 and template_id='58050000-0000-4000-8000-000000000000'");
        connection.commit();
        assertThatThrownBy(() -> migration.rollback(2, new Contexts(), new LabelExpression()))
                .hasStackTraceContaining("IC template was edited");
        assertThat(jdbc.queryForObject(
                        "select escalation_days from task_template_items where template_id='58050000-0000-4000-8000-000000000000' and sort_order=0",
                        Integer.class))
                .isEqualTo(3);
    }

    @Test
    void rollbackRefusesToDeleteTemplateWithCapturedHistory() throws Exception {
        jdbc.execute(
                "insert into task_template_versions(template_id,revision,fingerprint,trigger_status_id) select id,1,repeat('a',64),trigger_status_id from task_templates where id='"
                        + CONTRACT + "'");
        connection.commit();
        assertThatThrownBy(() -> migration.rollback(1, new Contexts(), new LabelExpression()))
                .hasStackTraceContaining("edited or used");
        assertThat(jdbc.queryForObject("select count(*) from task_template_versions", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from task_template_items", Integer.class))
                .isEqualTo(7);
    }

    private void baseline() throws Exception {
        migration.rollback(2, new Contexts(), new LabelExpression());
    }

    private void stage() throws Exception {
        connection.setAutoCommit(false);
        execute(ROOT + "seed/template_matrix_stage.sql");
        execute(ROOT + "20261027_contract_template_matrix.sql");
    }

    private void execute(String resource) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource);
                var statement = connection.createStatement()) {
            statement.execute(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private List<String> snapshot() {
        return jdbc.queryForList(
                """
                select (to_jsonb(i)-'created_at'-'updated_at')::text from task_template_items i order by id
                """,
                String.class);
    }

    private void update() throws Exception {
        Scope.getCurrentScope().getSingleton(FastCheckService.class).clearCache();
        migration.update(new Contexts(), new LabelExpression());
    }
}
