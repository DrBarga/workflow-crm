package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCommentMentionsIT extends ObjectCommentMentionsTestSupport {
    @Test
    void repeatedMentionsWithinTheTextLimitProduceOneNotification() throws Exception {
        UUID object = add("Лимит текста", manager, region);
        String name = label(lead);
        var selected = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 21; i++) selected.add(mention(lead, i * (name.length() + 1)));
        postComment(manager, object, body(String.join(" ", Collections.nCopies(21, name)), selected))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions.length()").value(21));
        assertThat(notifications(lead)).isEqualTo(1);
    }

    @Test
    void mentionsPersistWithHistoryAndNotifyEachRecipientOnceWithAnObjectLink() throws Exception {
        UUID object = add("Упоминания", manager, region);
        String name = label(lead);
        String text = name + " " + name + " " + label(director);
        var refs =
                List.of(mention(lead, 0), mention(lead, name.length() + 1), mention(director, 2 * (name.length() + 1)));
        var created = json.readTree(postComment(manager, object, body(text, refs))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(created.path("mentions")).hasSize(3);
        assertThat(comments(manager, object, Map.of()).path("items").get(0).path("mentions"))
                .isEqualTo(created.path("mentions"));
        assertThat(notifications(lead)).isEqualTo(1);
        assertThat(notifications(director)).isEqualTo(1);
        assertThat(notifications(manager)).isZero();
        var latest = json.readTree(mvc.perform(get("/api/notifications").cookie(cookie(lead)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(latest.get(0).path("type").asText()).isEqualTo("COMMENT_MENTIONED");
        assertThat(latest.get(0).path("objectUrl").asText()).isEqualTo("/api/objects/" + object);
        mvc.perform(get(latest.get(0).path("objectUrl").asText()).cookie(cookie(lead)))
                .andExpect(status().isOk());
        var history = json.readTree(jdbc.queryForObject(
                "select new_value from object_history where field_name='COMMENT_CREATED'", String.class));
        assertThat(history.path("mentions")).isEqualTo(created.path("mentions"));
        assertThat(jdbc.queryForObject("select version from objects where id=?", Long.class, object))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER", "CEO", "SUPERADMIN"})
    void allActiveCommentReadersCanBeMentionedIncludingReadOnlyRoles(String role) throws Exception {
        UUID object = add("Читатели", manager, region);
        UUID recipient = Map.of(
                        "MANAGER",
                        manager,
                        "REGIONAL_MANAGER",
                        lead,
                        "DEVELOPMENT_DIRECTOR",
                        director,
                        "CONTRACT_MANAGER",
                        contract,
                        "CEO",
                        ceo,
                        "SUPERADMIN",
                        admin)
                .get(role);
        postComment(manager, object, body(label(recipient), List.of(mention(recipient, 0))))
                .andExpect(status().isCreated());
        assertThat(notifications(recipient)).isEqualTo(recipient.equals(manager) ? 0 : 1);
    }

    @Test
    void inaccessibleInactiveMissingAndUnsupportedRecipientsRejectTheEntireComment() throws Exception {
        UUID object = add("Доступ", manager, region);
        UUID unsupported = UUID.randomUUID();
        user(unsupported, "MANAGER", region);
        jdbc.update("update users set status='DISABLED' where id=?", unsupported);
        for (UUID recipient : List.of(colleague, outsider, unsupported, UUID.randomUUID())) {
            String text = label(lead) + " " + label(manager);
            var invalid = Map.<String, Object>of(
                    "userId",
                    recipient.toString(),
                    "offset",
                    label(lead).length() + 1,
                    "length",
                    label(manager).length());
            postComment(manager, object, body(text, List.of(mention(lead, 0), invalid)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("mentions"));
        }
        assertThat(count("object_comments")).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(count("user_notifications")).isZero();
    }

    @Test
    void ordinaryAtTextDoesNotNotifyAndSelfMentionDoesNotNotify() throws Exception {
        UUID object = add("Без уведомления себе", manager, region);
        comment(manager, object, "mail@example.com " + label(lead));
        postComment(manager, object, body(label(manager), List.of(mention(manager, 0))))
                .andExpect(status().isCreated());
        assertThat(count("user_notifications")).isZero();
    }

    @Test
    void editNotifiesOnlyNewRecipientsAndMetadataOnlyChangesHaveVersions() throws Exception {
        UUID object = add("Изменение упоминаний", manager, region);
        String text = label(lead);
        var created = comment(manager, object, text);
        UUID id = id(created);
        var add = body(text, List.of(mention(lead, 0)));
        add.put("version", 0);
        var edited = json.readTree(patchComment(manager, object, id, add)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.edited").value(true))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(edited.path("editedAt").isNull()).isFalse();
        assertThat(notifications(lead)).isEqualTo(1);
        add.put("version", 1);
        patchComment(manager, object, id, add)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        add.put("text", text + " Проверить");
        patchComment(manager, object, id, add)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        assertThat(notifications(lead)).isEqualTo(1);
        patchComment(manager, object, id, add).andExpect(status().isConflict());
        var removed = body(text, List.of());
        removed.put("version", 2);
        patchComment(manager, object, id, removed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
        add.put("text", text);
        add.put("version", 3);
        patchComment(manager, object, id, add).andExpect(status().isOk());
        assertThat(notifications(lead)).isEqualTo(2);
    }

    @Test
    void readOnlyRolesAndForeignObjectManagerRemainForbiddenWithMentions() throws Exception {
        UUID object = add("Права автора", outsider, otherRegion);
        for (UUID writer : List.of(ceo, admin, manager))
            postComment(writer, object, body(label(director), List.of(mention(director, 0))))
                    .andExpect(status().isForbidden());
        assertThat(count("object_comments")).isZero();
        assertThat(count("user_notifications")).isZero();
    }

    @Test
    void notificationFailureRollsBackTextMentionsHistoryAndVersion() throws Exception {
        UUID object = add("Откат", manager, region);
        var original = comment(manager, object, label(lead));
        jdbc.execute(
                "alter table user_notifications add constraint test_comment_notification check(event_type<>'COMMENT_MENTIONED')");
        try {
            var input = body(label(lead), List.of(mention(lead, 0)));
            postComment(manager, object, input).andExpect(status().isInternalServerError());
            input.put("version", 0);
            patchComment(manager, object, id(original), input).andExpect(status().isInternalServerError());
            var saved = comments(manager, object, Map.of()).path("items").get(0);
            assertThat(saved.path("version").asLong()).isZero();
            assertThat(saved.path("mentions")).isEmpty();
            assertThat(saved.path("edited").asBoolean()).isFalse();
            assertThat(count("object_comments")).isEqualTo(1);
            assertThat(count("object_history")).isEqualTo(1);
            assertThat(count("user_notifications")).isZero();
        } finally {
            jdbc.execute("alter table user_notifications drop constraint test_comment_notification");
        }
    }

    @Test
    void concurrentMentionEditsNotifyOnlyForTheCommittedVersion() throws Exception {
        UUID object = add("Одновременное редактирование", manager, region);
        UUID comment = id(comment(manager, object, label(lead)));
        var input = body(label(lead), List.of(mention(lead, 0)));
        input.put("version", 0);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> edit = () -> {
                barrier.await();
                return patchComment(manager, object, comment, input)
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            var first = pool.submit(edit);
            var second = pool.submit(edit);
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(notifications(lead)).isEqualTo(1);
    }
}
