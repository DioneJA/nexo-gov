package br.com.nexo.gov.organization;

import br.com.nexo.gov.organization.OrganizationContactDtos.ContactType;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class OrganizationContactCsv {
    static final int MAX_BYTES = 1_048_576;
    static final int MAX_ROWS = 1_000;
    private static final List<String> EXPORT_HEADERS = List.of("id", "type", "name", "email", "phone", "active");
    private static final Set<String> ALLOWED_HEADERS = Set.of("id", "type", "name", "email", "phone", "active");

    record ImportRow(int rowNumber, UUID id, ContactType type, String name,
                     String email, String phone, Boolean active) {}

    static List<ImportRow> parse(byte[] bytes) {
        if (bytes.length == 0) throw new CsvException("O arquivo CSV está vazio.");
        if (bytes.length > MAX_BYTES) throw new CsvException("O arquivo CSV excede o limite de 1 MiB.");

        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new CsvException("O arquivo CSV precisa estar codificado em UTF-8.");
        }
        if (content.startsWith("\uFEFF")) content = content.substring(1);

        List<List<String>> records = parseRecords(content);
        records.removeIf(record -> record.size() == 1 && record.getFirst().isEmpty());
        if (records.size() < 2) throw new CsvException("O CSV precisa ter cabeçalho e ao menos uma linha de contato.");

        List<String> headers = records.getFirst().stream().map(value -> value.trim().toLowerCase()).toList();
        if (headers.stream().distinct().count() != headers.size()) throw new CsvException("O cabeçalho CSV contém colunas repetidas.");
        if (!headers.contains("type") || !headers.contains("name")) {
            throw new CsvException("O cabeçalho deve conter as colunas type e name.");
        }
        if (!ALLOWED_HEADERS.containsAll(headers)) throw new CsvException("O cabeçalho CSV contém uma coluna não reconhecida.");

        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) positions.put(headers.get(i), i);
        if (records.size() - 1 > MAX_ROWS) throw new CsvException("O arquivo CSV pode conter no máximo 1.000 contatos por importação.");

        List<ImportRow> rows = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            int rowNumber = index + 1;
            if (record.size() != headers.size()) throw rowError(rowNumber, "a quantidade de campos não corresponde ao cabeçalho");
            try {
                UUID id = value(record, positions, "id").isBlank() ? null : UUID.fromString(value(record, positions, "id").trim());
                if (id != null && !ids.add(id)) throw rowError(rowNumber, "o mesmo ID aparece mais de uma vez");
                ContactType type = ContactType.valueOf(value(record, positions, "type").trim());
                String name = unescapeSpreadsheet(value(record, positions, "name")).trim();
                String email = blankToNull(unescapeSpreadsheet(value(record, positions, "email")));
                String phone = blankToNull(unescapeSpreadsheet(value(record, positions, "phone")));
                Boolean active = optionalBoolean(record, positions, "active", rowNumber);

                if (name.isEmpty()) throw rowError(rowNumber, "name é obrigatório");
                if (name.length() > 180) throw rowError(rowNumber, "name deve ter até 180 caracteres");
                if (email != null && (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                    throw rowError(rowNumber, "email não é válido");
                }
                if (phone != null && phone.length() > 40) throw rowError(rowNumber, "phone deve ter até 40 caracteres");
                rows.add(new ImportRow(rowNumber, id, type, name, email, phone, active));
            } catch (IllegalArgumentException invalid) {
                if (invalid instanceof CsvException csvException) throw csvException;
                throw rowError(rowNumber, "type, id ou active não tem um valor válido");
            }
        }
        return List.copyOf(rows);
    }

    static byte[] export(List<OrganizationContactDtos.ContactView> contacts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "\uFEFF" + String.join(",", EXPORT_HEADERS) + "\r\n");
        for (OrganizationContactDtos.ContactView contact : contacts) {
            write(out, csv(contact.id().toString()) + "," + csv(contact.type().name()) + "," + csv(contact.name()) + ","
                    + csv(contact.email()) + "," + csv(contact.phone()) + "," + contact.active() + "\r\n");
        }
        return out.toByteArray();
    }

    private static List<List<String>> parseRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        boolean touched = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else field.append(c);
                continue;
            }

            if (closedQuote && c != ',' && c != '\r' && c != '\n') throw new CsvException("Há conteúdo após um campo CSV entre aspas.");
            if (c == '"') {
                if (field.length() != 0 || closedQuote) throw new CsvException("Aspas CSV fora do início do campo.");
                quoted = true;
                touched = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                closedQuote = false;
                touched = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                record.add(field.toString());
                if (touched || record.size() > 1 || !field.isEmpty()) records.add(List.copyOf(record));
                record.clear();
                field.setLength(0);
                closedQuote = false;
                touched = false;
            } else {
                if (closedQuote) throw new CsvException("Há conteúdo após um campo CSV entre aspas.");
                field.append(c);
                if (!Character.isWhitespace(c)) touched = true;
            }
        }
        if (quoted) throw new CsvException("O arquivo CSV termina com aspas não fechadas.");
        if (touched || record.size() > 0 || !field.isEmpty()) {
            record.add(field.toString());
            records.add(List.copyOf(record));
        }
        return records;
    }

    private static Boolean optionalBoolean(List<String> record, Map<String, Integer> positions, String name, int row) {
        if (!positions.containsKey(name) || value(record, positions, name).isBlank()) return null;
        String raw = value(record, positions, name).trim();
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        throw rowError(row, "active deve ser true ou false");
    }

    private static String value(List<String> record, Map<String, Integer> positions, String name) {
        Integer position = positions.get(name);
        return position == null ? "" : record.get(position);
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.startsWith("'") || safe.matches("^[\\s\\u0000-\\u001F]*[=+@-].*")) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String unescapeSpreadsheet(String value) {
        if (value.startsWith("''")) return value.substring(1);
        if (value.length() > 1 && value.charAt(0) == '\'' && "=+@-".indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static CsvException rowError(int row, String message) {
        return new CsvException("Linha " + row + ": " + message + ".");
    }

    static final class CsvException extends IllegalArgumentException {
        CsvException(String message) { super(message); }
    }
}
