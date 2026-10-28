package dev.workflowcrm.integrations.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.workflowcrm.integrations.api.BinaryStorageException;
import io.minio.*;
import io.minio.messages.Item;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MinioBinaryStorageTest {
    private final MinioClient client = mock(MinioClient.class);
    private final MinioBinaryStorage storage = new MinioBinaryStorage(client);

    @Test
    void purgesEveryVersionAndMarkerWithoutDeletingPrefixNeighbours() throws Exception {
        var initial = List.of(item("key", "v1"), item("key", "v2"), item("key-other", "v3"));
        var remaining = List.of(item("key-other", "v3"));
        when(client.listObjects(any())).thenReturn(initial, remaining);
        storage.deleteAllVersions("documents", "key");
        var removed = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client, times(2)).removeObject(removed.capture());
        assertThat(removed.getAllValues())
                .extracting(RemoveObjectArgs::versionId)
                .containsExactly("v1", "v2");
        assertThat(removed.getAllValues()).extracting(RemoveObjectArgs::object).containsOnly("key");
    }

    @Test
    void emptyListingMakesRetryIdempotent() throws Exception {
        when(client.listObjects(any())).thenReturn(List.of());
        storage.deleteAllVersions("documents", "key");
        verify(client, never()).removeObject(any());
    }

    @Test
    void lazyListingErrorsCannotBeIgnored() throws Exception {
        when(client.listObjects(any())).thenReturn(List.of(new Result<>(new java.io.IOException("offline"))));
        assertThatThrownBy(() -> storage.deleteAllVersions("documents", "key"))
                .isInstanceOf(BinaryStorageException.class);
    }

    @Test
    void remainingVersionPreventsSuccess() throws Exception {
        var listing = List.of(item("key", "v1"));
        when(client.listObjects(any())).thenReturn(listing);
        assertThatThrownBy(() -> storage.deleteAllVersions("documents", "key"))
                .isInstanceOf(BinaryStorageException.class);
    }

    private Result<Item> item(String name, String version) {
        var value = mock(Item.class);
        when(value.objectName()).thenReturn(name);
        when(value.versionId()).thenReturn(version);
        return new Result<>(value);
    }
}
