package dev.workflowcrm.objects.domain;

public interface ObjectListCursors {
    int MAX_LENGTH = 4096;

    ObjectListPosition read(String token, ObjectListFilter filter, ObjectListScope scope);

    String write(ObjectListPosition position, ObjectListFilter filter, ObjectListScope scope);
}
