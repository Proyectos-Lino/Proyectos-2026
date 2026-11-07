package com.admision.lector_dbf.importer.reader;

import com.admision.lector_dbf.importer.model.DbfPreviewModels.*;
import com.admision.lector_dbf.importer.model.ImportModels.*;
import com.admision.lector_dbf.shared.ApiException;
import com.linuxense.javadbf.DBFReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DbfParser {
    public DbfFiles readFiles(MultipartFile identification, MultipartFile responses, MultipartFile keys) {
        return new DbfFiles(read(identification), read(responses), read(keys));
    }

    public Inspection inspect(DbfFiles files) {
        return new Inspection(columns(files.identification()), columns(files.responses()),
                columns(files.keys()), defaultMapping(files));
    }

    private FileColumns columns(DbfTable table) {
        return new FileColumns(table.columns(), table.rows().size(),
                table.rows().stream().limit(3).map(RawRow::values).toList());
    }

    public ColumnMapping defaultMapping(DbfFiles files) {
        requireColumns(files.identification(), 3);
        requireColumns(files.responses(), 4);
        requireColumns(files.keys(), 4);
        return new ColumnMapping(files.identification().columns().get(0),
                files.identification().columns().get(1), files.identification().columns().get(2),
                files.responses().columns().get(0), files.responses().columns().get(1),
                files.responses().columns().get(3), files.keys().columns().get(1),
                files.keys().columns().get(3));
    }

    public MappedRows map(DbfFiles files, ColumnMapping mapping) {
        if (mapping == null) throw ApiException.badRequest("Selecciona el mapeo de columnas DBF");
        int idSheet = index(files.identification(), mapping.identificationSheet());
        int idForm = index(files.identification(), mapping.identificationForm());
        int idParticipant = index(files.identification(), mapping.identificationParticipant());
        int responseSheet = index(files.responses(), mapping.responseSheet());
        int responseForm = index(files.responses(), mapping.responseForm());
        int responseAnswers = index(files.responses(), mapping.responseAnswersFrom());
        int keyForm = index(files.keys(), mapping.keyForm());
        int keyAnswers = index(files.keys(), mapping.keyAnswersFrom());
        if (idSheet == idForm || idSheet == idParticipant || idForm == idParticipant
                || responseSheet == responseForm || responseAnswers == responseSheet
                || responseAnswers == responseForm || keyAnswers == keyForm)
            throw ApiException.badRequest("El mapeo de columnas tiene campos repetidos");
        List<IdentificationRow> identities = new ArrayList<>();
        for (RawRow row : files.identification().rows())
            identities.add(new IdentificationRow(row.number(), new Identification(
                    row.values().get(idSheet), row.values().get(idForm), row.values().get(idParticipant))));
        List<ResponseRow> responses = new ArrayList<>();
        for (RawRow row : files.responses().rows())
            responses.add(new ResponseRow(row.number(), new Response(row.values().get(responseSheet),
                    row.values().get(responseForm), answers(row, responseAnswers))));
        List<KeyRow> keys = new ArrayList<>();
        for (RawRow row : files.keys().rows())
            keys.add(new KeyRow(row.number(), new Key(row.values().get(keyForm), answers(row, keyAnswers))));
        return new MappedRows(identities, responses, keys);
    }

    public Parsed parse(MultipartFile identification, MultipartFile responses, MultipartFile keys) {
        DbfFiles files = readFiles(identification, responses, keys);
        return parse(files, defaultMapping(files));
    }

    public Parsed parse(DbfFiles files, ColumnMapping mapping) {
        MappedRows rows = map(files, mapping);
        Map<String, Identification> identities = new HashMap<>();
        Map<String, Key> answerKeys = new HashMap<>();
        List<Response> responses = new ArrayList<>();
        for (IdentificationRow row : rows.identifications()) {
            Identification identity = row.value();
            if (identity.sheetCode().isBlank() || identity.participantCode().isBlank() || identity.formCode().isBlank())
                throw ApiException.badRequest("Identificación DBF incompleta en la fila " + row.number());
            if (identities.putIfAbsent(identity.sheetCode(), identity) != null)
                throw ApiException.badRequest("Código de hoja duplicado en IDENTIFI, fila " + row.number());
        }
        for (KeyRow row : rows.keys()) {
            Key key = row.value();
            if (key.formCode().isBlank() || answerKeys.putIfAbsent(key.formCode().toUpperCase(), key) != null)
                throw ApiException.badRequest("Tema inválido o duplicado en CLAVES, fila " + row.number());
        }
        for (ResponseRow row : rows.responses()) responses.add(row.value());
        if (identities.isEmpty() || responses.isEmpty() || answerKeys.isEmpty())
            throw ApiException.badRequest("Los archivos DBF no contienen todos los registros necesarios");
        return new Parsed(identities, responses, answerKeys);
    }

    private DbfTable read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".dbf"))
            throw ApiException.badRequest("Selecciona un archivo DBF válido");
        try (InputStream stream = file.getInputStream()) {
            DBFReader reader = new DBFReader(stream);
            List<String> names = new ArrayList<>();
            for (int column = 0; column < reader.getFieldCount(); column++)
                names.add(reader.getField(column).getName());
            List<RawRow> rows = new ArrayList<>();
            Object[] values;
            while ((values = reader.nextRecord()) != null) {
                List<String> cells = new ArrayList<>();
                for (Object value : values) cells.add(value == null ? "" : value.toString().trim());
                rows.add(new RawRow(rows.size() + 1, cells));
            }
            return new DbfTable(names, rows);
        } catch (Exception exception) {
            throw ApiException.badRequest("No se pudo leer el archivo DBF " + file.getOriginalFilename());
        }
    }

    private int index(DbfTable table, String column) {
        if (column == null || column.isBlank()) throw ApiException.badRequest("Selecciona todas las columnas necesarias");
        for (int index = 0; index < table.columns().size(); index++)
            if (table.columns().get(index).equalsIgnoreCase(column)) return index;
        throw ApiException.badRequest("La columna '" + column + "' no existe en el archivo DBF seleccionado");
    }

    private void requireColumns(DbfTable table, int minimum) {
        if (table.columns().size() < minimum)
            throw ApiException.badRequest("El archivo DBF tiene menos columnas de las necesarias");
    }

    private List<String> answers(RawRow row, int start) {
        return row.values().subList(start, row.values().size()).stream().map(String::toUpperCase).toList();
    }
}
