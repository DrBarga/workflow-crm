package dev.workflowcrm.contacts.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

final class ContactDeletionConfirmation {
    private ContactDeletionConfirmation() {}

    static String token(UUID actorId, Contact contact, List<ContactObject> objects) {
        var state = new StringBuilder()
                .append(actorId)
                .append('|')
                .append(contact.getId())
                .append('|')
                .append(contact.getVersion());
        objects.stream().sorted(Comparator.comparing(ContactObject::id)).forEach(o -> state.append('|')
                .append(o.id())
                .append(':')
                .append(o.version())
                .append(':')
                .append(o.status())
                .append(':')
                .append(o.finalStatus()));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(state.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
