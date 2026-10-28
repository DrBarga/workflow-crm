package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.ObjectComment;
import dev.workflowcrm.objects.domain.ObjectCommentInput;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class ObjectCommentParser {
    record Edit(ObjectCommentInput input, long version) {}

    ObjectCommentInput create(JsonNode body) {
        fields(body, Set.of("text", "mentions"));
        return input(body);
    }

    Edit edit(JsonNode body) {
        fields(body, Set.of("text", "version", "mentions"));
        var version = body.path("version");
        if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0)
            throw new UnprocessableInputException("version", "Provide the current comment version");
        return new Edit(input(body), version.longValue());
    }

    private ObjectCommentInput input(JsonNode body) {
        var selected = new ArrayList<ObjectComment.Mention>();
        if (body.has("mentions")) {
            var mentions = body.path("mentions");
            if (!mentions.isArray() || mentions.size() > ObjectCommentInput.MAX_MENTIONS) throw invalidMentions();
            for (var mention : mentions) {
                if (!mention.isObject()
                        || mention.size() != 3
                        || !mention.path("userId").isTextual()
                        || !mention.path("offset").isIntegralNumber()
                        || !mention.path("offset").canConvertToInt()
                        || !mention.path("length").isIntegralNumber()
                        || !mention.path("length").canConvertToInt()) throw invalidMentions();
                UUID user;
                try {
                    String value = mention.path("userId").textValue();
                    user = UUID.fromString(value);
                    if (!user.toString().equalsIgnoreCase(value)) throw invalidMentions();
                } catch (IllegalArgumentException invalid) {
                    throw invalidMentions();
                }
                selected.add(new ObjectComment.Mention(
                        user,
                        mention.path("offset").intValue(),
                        mention.path("length").intValue()));
            }
        }
        return new ObjectCommentInput(text(body), selected);
    }

    private UnprocessableInputException invalidMentions() {
        return new UnprocessableInputException(
                "mentions", "Provide mentions with userId, offset and length within the comment text limit");
    }

    private String text(JsonNode body) {
        if (!body.path("text").isTextual()) throw new UnprocessableInputException("text", "Provide comment text");
        return body.path("text").textValue();
    }

    private void fields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw new UnprocessableInputException("body", "Provide a JSON object");
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new UnprocessableInputException(field, "Unknown or protected field");
        });
    }
}
