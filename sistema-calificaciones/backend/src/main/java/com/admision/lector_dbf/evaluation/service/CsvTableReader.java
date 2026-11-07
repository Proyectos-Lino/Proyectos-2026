package com.admision.lector_dbf.evaluation.service;

import com.admision.lector_dbf.shared.ApiException;
import com.admision.lector_dbf.evaluation.model.CsvTable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CsvTableReader {
    /** Cabeceras por nombre, fila lógica original y columnas opcionales para inscripciones. */
    public CsvTable readMapped(MultipartFile file, List<String> required, List<String> allowed) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Selecciona un archivo CSV");
        if (file.getSize() > 2_000_000) throw ApiException.badRequest("El CSV no puede superar 2 MB");
        try {
            String content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(file.getBytes())).toString().replaceFirst("^\\uFEFF", "");
            char delimiter = content.lines().findFirst().orElse("").contains(";") ? ';' : ',';
            var parsed = parse(content, delimiter);
            if (parsed.isEmpty()) throw ApiException.badRequest("El CSV no contiene cabecera");
            var header = parsed.get(0).stream().map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList();
            if (!header.containsAll(required) || !allowed.containsAll(header) || new java.util.HashSet<>(header).size() != header.size())
                throw ApiException.badRequest("Revisa las columnas. Requeridas: " + String.join(", ", required) + ". Permitidas: " + String.join(", ", allowed));
            var rows = new ArrayList<CsvTable.Row>();
            for (int index = 1; index < parsed.size(); index++) {
                var values = parsed.get(index);
                if (values.stream().allMatch(String::isBlank)) continue;
                if (values.size() != header.size()) throw ApiException.badRequest("Fila " + (index + 1) + ": número de columnas incorrecto");
                var fields = new java.util.LinkedHashMap<String, String>();
                for (int column = 0; column < header.size(); column++) fields.put(header.get(column), values.get(column));
                rows.add(new CsvTable.Row(index + 1, java.util.Map.copyOf(fields)));
            }
            if (rows.isEmpty() || rows.size() > 5000) throw ApiException.badRequest("El CSV debe contener entre 1 y 5000 registros");
            return new CsvTable(header, List.copyOf(rows));
        } catch (java.io.IOException exception) {
            throw ApiException.badRequest("No se pudo leer el CSV. Guarda el archivo con codificación UTF-8");
        }
    }

    public List<List<String>> read(MultipartFile file, List<String> expectedHeader, String... optionalColumns) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Selecciona un archivo CSV");
        if (file.getSize() > 2_000_000) throw ApiException.badRequest("El CSV no puede superar 2 MB");
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
            char delimiter = content.lines().findFirst().orElse("").contains(";") ? ';' : ',';
            List<List<String>> rows = parse(content, delimiter);
            List<String> fullHeader=new ArrayList<>(expectedHeader);
            fullHeader.addAll(List.of(optionalColumns));
            List<String> actual=rows.isEmpty() ? List.of() : rows.get(0).stream().map(String::toLowerCase).toList();
            if (!actual.equals(expectedHeader) && !actual.equals(fullHeader))
                throw ApiException.badRequest("Las columnas del CSV deben ser: " + String.join(", ", fullHeader));
            rows.remove(0);
            rows.removeIf(row -> row.stream().allMatch(String::isBlank));
            if (rows.isEmpty()) throw ApiException.badRequest("El CSV no contiene registros");
            if (rows.size() > 5000) throw ApiException.badRequest("El CSV admite hasta 5000 registros");
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).size() != actual.size())
                    throw ApiException.badRequest("Fila " + (i + 2) + ": número de columnas incorrecto");
                while (rows.get(i).size()<fullHeader.size()) rows.get(i).add("");
            }
            return rows;
        } catch (java.io.IOException exception) {
            throw ApiException.badRequest("No se pudo leer el archivo CSV");
        }
    }

    private List<List<String>> parse(String content, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') { quoted = false; afterQuote = true; }
                else field.append(c);
            } else if (c == '"' && field.isEmpty() && !afterQuote) {
                quoted = true;
            } else if (c == delimiter || c == '\n' || c == '\r') {
                row.add(field.toString().trim()); field.setLength(0); afterQuote = false;
                if (c != delimiter) {
                    rows.add(row); row = new ArrayList<>();
                    if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                }
            } else if (afterQuote && !Character.isWhitespace(c)) {
                throw ApiException.badRequest("El CSV contiene comillas mal formadas");
            } else if (!afterQuote) {
                field.append(c);
            }
        }
        if (quoted) throw ApiException.badRequest("El CSV contiene comillas sin cerrar");
        if (!row.isEmpty() || !field.isEmpty()) { row.add(field.toString().trim()); rows.add(row); }
        return rows;
    }
}
