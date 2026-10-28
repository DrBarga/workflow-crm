package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Component;

@Component
class JdbcObjectExportRenderer implements ObjectExportRenderer {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcObjectExportRenderer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long render(ObjectExport.Job job, Path file, long maxBytes) throws IOException {
        var columns = ObjectExportColumns.forScope(job.scope());
        var names = columns.stream().map(ObjectExportColumns::name).toList();
        try (var output = new LimitedOutput(Files.newOutputStream(file), maxBytes);
                var table = ObjectExportTable.open(job.format(), output, names, maxBytes)) {
            long[] count = {0};
            if (!job.scope().regional() || !job.scope().regionIds().isEmpty()) {
                var parameters = new MapSqlParameterSource().addValue("actor", job.actor());
                String select = columns.stream()
                        .map(c -> c.pin() || !job.scope().role().equals("MANAGER")
                                ? c.sql()
                                : "case when o.manager_id=:actor then " + c.sql() + " end")
                        .collect(Collectors.joining(","));
                String sql = "select " + select + " from objects o join object_statuses s on s.id=o.status_id"
                        + " left join object_types t on t.id=o.object_type_id"
                        + " left join contract_types ct on ct.id=o.contract_type_id"
                        + " left join object_statuses paused on paused.id=o.status_before_pause_id"
                        + " left join object_statuses refused on refused.id=o.status_before_refusal_id"
                        + " left join refusal_reasons reason on reason.id=o.refusal_reason_id"
                        + ObjectListPredicates.forExport(job.filter(), job.scope(), job.requestedAt(), parameters)
                        + " order by " + ObjectListOrder.of(job.filter()).ordering();
                var parsed = NamedParameterUtils.parseSqlStatement(sql);
                var factory = new PreparedStatementCreatorFactory(
                        NamedParameterUtils.substituteNamedParameters(parsed, parameters),
                        NamedParameterUtils.buildSqlParameterList(parsed, parameters));
                var statement = factory.newPreparedStatementCreator(
                        NamedParameterUtils.buildValueArray(parsed, parameters, null));
                jdbc.getJdbcTemplate()
                        .query(
                                connection -> {
                                    var prepared = statement.createPreparedStatement(connection);
                                    prepared.setFetchSize(32);
                                    return prepared;
                                },
                                (org.springframework.jdbc.core.RowCallbackHandler) result -> {
                                    var values = new ArrayList<Object>(columns.size());
                                    for (int i = 1; i <= columns.size(); i++) values.add(result.getObject(i));
                                    try {
                                        table.row(values);
                                    } catch (IOException error) {
                                        throw new UncheckedIOException(error);
                                    }
                                    count[0]++;
                                });
            }
            table.finish();
            return count[0];
        }
    }

    private static final class LimitedOutput extends FilterOutputStream {
        private final long limit;
        private long bytes;

        LimitedOutput(OutputStream output, long limit) {
            super(output);
            this.limit = limit;
        }

        private void count(int size) {
            bytes += size;
            if (bytes > limit) throw new ObjectExportLimitException();
        }

        public void write(int value) throws IOException {
            count(1);
            out.write(value);
        }

        public void write(byte[] value, int offset, int length) throws IOException {
            count(length);
            out.write(value, offset, length);
        }
    }
}
