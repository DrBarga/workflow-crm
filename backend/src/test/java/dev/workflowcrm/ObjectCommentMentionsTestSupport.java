package dev.workflowcrm;

import java.util.*;

abstract class ObjectCommentMentionsTestSupport extends ObjectCommentTestSupport {
    String label(UUID user) {
        return "@" + jdbc.queryForObject("select full_name from users where id=?", String.class, user);
    }

    Map<String, Object> mention(UUID user, int offset) {
        return Map.of(
                "userId",
                user.toString(),
                "offset",
                offset,
                "length",
                label(user).length());
    }

    Map<String, Object> body(String text, List<Map<String, Object>> mentions) {
        return new LinkedHashMap<>(Map.of("text", text, "mentions", mentions));
    }

    int notifications(UUID recipient) {
        return jdbc.queryForObject(
                "select count(*) from user_notifications where recipient_id=? and event_type='COMMENT_MENTIONED'",
                Integer.class,
                recipient);
    }
}
