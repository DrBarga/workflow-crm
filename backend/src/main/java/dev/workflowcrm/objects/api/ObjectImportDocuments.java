package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectImport;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.commons.csv.*;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
class ObjectImportDocuments {
    private static final int MAX_ROWS = 10000;
    private static final CSVFormat CSV =
            CSVFormat.RFC4180.builder().setIgnoreEmptyLines(false).get();

    List<ObjectImport.Input> read(String name, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 20 * 1024 * 1024)
            throw invalid("Upload a non-empty file of at most 20 MB");
        String extension = name == null ? "" : name.toLowerCase(Locale.ROOT);
        try {
            if (extension.endsWith(".csv")) return csv(bytes);
            if (extension.endsWith(".xlsx")) return xlsx(bytes);
            throw invalid("Use the CSV or XLSX import template");
        } catch (IOException | IllegalArgumentException | UncheckedIOException | POIXMLException e) {
            throw invalid("File cannot be read as the selected import template");
        }
    }

    byte[] template(String format) {
        try {
            var output = new ByteArrayOutputStream();
            if (format.equals("csv")) {
                try (var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                        var printer = new CSVPrinter(writer, CSV)) {
                    writer.write('\ufeff');
                    printer.printRecord(ObjectImportTemplate.COLUMNS);
                }
            } else if (format.equals("xlsx")) {
                try (var book = new XSSFWorkbook()) {
                    var sheet = book.createSheet("Objects");
                    var row = sheet.createRow(0);
                    for (int i = 0; i < ObjectImportTemplate.COLUMNS.size(); i++)
                        row.createCell(i).setCellValue(ObjectImportTemplate.COLUMNS.get(i));
                    sheet.createFreezePane(0, 1);
                    book.write(output);
                }
            } else throw new UnprocessableInputException("format", "Use csv or xlsx");
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot generate import template", e);
        }
    }

    private List<ObjectImport.Input> csv(byte[] bytes) throws IOException {
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new PushbackReader(new InputStreamReader(new ByteArrayInputStream(bytes), decoder), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\ufeff') reader.unread(first);
            try (var parser = CSV.parse(reader)) {
                var iterator = parser.iterator();
                if (!iterator.hasNext()) throw invalid("Template header is missing");
                header(iterator.next().toList());
                var rows = new ArrayList<ObjectImport.Input>();
                while (iterator.hasNext()) {
                    var record = iterator.next();
                    rows.add(input(Math.toIntExact(record.getRecordNumber()), record.toList()));
                    limit(rows.size());
                }
                return rows;
            }
        }
    }

    private List<ObjectImport.Input> xlsx(byte[] bytes) throws IOException {
        long expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > 64 * 1024 * 1024) throw invalid("Expanded workbook exceeds 64 MB");
                }
            }
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() != 1 || !book.getSheetName(0).equals("Objects"))
                throw invalid("Workbook must contain one sheet named Objects");
            var sheet = book.getSheetAt(0);
            limit(sheet.getLastRowNum());
            header(values(sheet.getRow(0)));
            var rows = new ArrayList<ObjectImport.Input>();
            for (int n = 1; n <= sheet.getLastRowNum(); n++) rows.add(input(n + 1, values(sheet.getRow(n))));
            return rows;
        }
    }

    private List<String> values(Row row) {
        var values = new ArrayList<String>();
        int length = row == null
                ? ObjectImportTemplate.COLUMNS.size()
                : Math.max(row.getLastCellNum(), ObjectImportTemplate.COLUMNS.size());
        if (length > ObjectImportTemplate.COLUMNS.size()) throw invalid("Unexpected workbook columns");
        for (int i = 0; i < length; i++) {
            Cell cell = row == null ? null : row.getCell(i);
            String value = cell == null
                    ? ""
                    : switch (cell.getCellType()) {
                        case BLANK -> "";
                        case STRING -> cell.getStringCellValue();
                        case NUMERIC ->
                            DateUtil.isCellDateFormatted(cell)
                                    ? cell.getLocalDateTimeCellValue()
                                            .toLocalDate()
                                            .toString()
                                    : org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
                        default -> "\u0000Unsupported cell type; formulas are not accepted";
                    };
            values.add(value);
        }
        return values;
    }

    private void header(List<String> values) {
        if (!values.equals(ObjectImportTemplate.COLUMNS))
            throw invalid("Columns and order must match the downloaded template");
    }

    private ObjectImport.Input input(int number, List<String> cells) {
        if (cells.size() == 1 && cells.getFirst().isBlank())
            cells = Collections.nCopies(ObjectImportTemplate.COLUMNS.size(), "");
        if (cells.size() != ObjectImportTemplate.COLUMNS.size())
            throw invalid("Row " + number + " has an unexpected number of columns");
        var values = new LinkedHashMap<String, String>();
        for (int n = 0; n < cells.size(); n++) {
            values.put(ObjectImportTemplate.COLUMNS.get(n), cells.get(n));
        }
        return new ObjectImport.Input(number, values);
    }

    private void limit(int rows) {
        if (rows > MAX_ROWS) throw invalid("Import supports at most 10000 data rows");
    }

    private static UnprocessableInputException invalid(String message) {
        return new UnprocessableInputException("file", message);
    }
}
