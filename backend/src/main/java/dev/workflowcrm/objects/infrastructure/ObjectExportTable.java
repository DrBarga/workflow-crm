package dev.workflowcrm.objects.infrastructure;

import dev.workflowcrm.objects.domain.ObjectExport;
import dev.workflowcrm.objects.domain.ObjectExportLimitException;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

interface ObjectExportTable extends AutoCloseable {
    void row(List<Object> values) throws IOException;

    void finish() throws IOException;

    void close() throws IOException;

    static ObjectExportTable open(ObjectExport.Format format, OutputStream output, List<String> headers, long maxBytes)
            throws IOException {
        ObjectExportTable table =
                format == ObjectExport.Format.CSV ? new Csv(output) : new Xlsx(output, headers, maxBytes);
        if (format == ObjectExport.Format.CSV)
            table.row(headers.stream().map(v -> (Object) v).toList());
        return table;
    }

    static String text(Object value) {
        if (value == null) return "";
        if (value instanceof java.sql.Timestamp timestamp)
            return timestamp.toInstant().toString();
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        return value.toString();
    }

    final class Csv implements ObjectExportTable {
        private final Writer writer;

        Csv(OutputStream output) throws IOException {
            writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write('\ufeff');
        }

        public void row(List<Object> values) throws IOException {
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) writer.write(',');
                Object value = values.get(i);
                String text = text(value);
                if (text.length() > 1048576) throw new ObjectExportLimitException();
                if (value instanceof String && formula(text)) text = "'" + text;
                writer.write('"');
                writer.write(text.replace("\"", "\"\""));
                writer.write('"');
            }
            writer.write("\r\n");
        }

        private boolean formula(String value) {
            int i = 0;
            while (i < value.length()
                    && (Character.isWhitespace(value.charAt(i)) || Character.isISOControl(value.charAt(i)))) i++;
            return i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0;
        }

        public void finish() throws IOException {
            writer.flush();
        }

        public void close() throws IOException {
            writer.close();
        }
    }

    final class Xlsx implements ObjectExportTable {
        private final SXSSFWorkbook workbook = new SXSSFWorkbook(10);
        private final OutputStream output;
        private final List<String> headers;
        private final long maxBytes;
        private Sheet sheet;
        private int row;
        private long characters;

        Xlsx(OutputStream output, List<String> headers, long maxBytes) {
            this.output = output;
            this.headers = headers;
            this.maxBytes = maxBytes;
            workbook.setCompressTempFiles(true);
            nextSheet();
        }

        private void nextSheet() {
            sheet = workbook.createSheet("Objects " + (workbook.getNumberOfSheets() + 1));
            sheet.createFreezePane(0, 1);
            var heading = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) heading.createCell(i).setCellValue(headers.get(i));
            row = 1;
        }

        public void row(List<Object> values) {
            if (row >= 1048576) nextSheet();
            var target = sheet.createRow(row++);
            for (int i = 0; i < values.size(); i++) {
                String text = text(values.get(i));
                characters += text.length();
                if (text.length() > 32767 || characters > maxBytes) throw new ObjectExportLimitException();
                var cell = target.createCell(i);
                if (values.get(i) instanceof BigDecimal decimal && decimal.precision() <= 15)
                    cell.setCellValue(decimal.doubleValue());
                else cell.setCellValue(text);
            }
        }

        public void finish() throws IOException {
            workbook.write(output);
        }

        public void close() throws IOException {
            workbook.close();
        }
    }
}
