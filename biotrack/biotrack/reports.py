"""Exportaciones CSV, XLSX y PDF sin servicios externos."""
from __future__ import annotations

import csv
import io
import zipfile
from xml.sax.saxutils import escape

HEADERS = ("Fecha", "Hora", "Nombre", "Apellido", "Cargo", "Acción", "Origen", "Observación")


def records(rows):
    return [[r["fecha"], r["hora"], r["nombre"], r["apellido"], r["cargo"], r["accion_texto"], r["origen"], r.get("observacion", "")] for r in rows]


def safe_cell(value):
    text = str(value)
    return "'" + text if text.lstrip().startswith(("=", "+", "-", "@")) else text


def csv_bytes(rows):
    stream = io.StringIO(newline="")
    writer = csv.writer(stream, delimiter=";")
    writer.writerow(HEADERS)
    writer.writerows([[safe_cell(cell) for cell in row] for row in records(rows)])
    return stream.getvalue().encode("utf-8-sig")


def xlsx_bytes(rows):
    cells = []
    for number, row in enumerate([HEADERS] + records(rows), 1):
        line = []
        for col, value in enumerate(row):
            address = f"{chr(65 + col)}{number}"
            style = ' s="1"' if number == 1 else ""
            # Caracteres de control no permitidos por XML 1.0.
            value = "".join(c for c in safe_cell(value) if ord(c) >= 32 or c in "\n\r\t")
            line.append(f'<c r="{address}" t="inlineStr"{style}><is><t xml:space="preserve">{escape(value)}</t></is></c>')
        cells.append(f'<row r="{number}">{"".join(line)}</row>')
    sheet = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="2" width="15" customWidth="1"/><col min="3" max="4" width="22" customWidth="1"/><col min="5" max="7" width="25" customWidth="1"/><col min="8" max="8" width="50" customWidth="1"/></cols><sheetData>' + "".join(cells) + f'</sheetData><autoFilter ref="A1:H{len(rows) + 1}"/></worksheet>'
    files = {
        "[Content_Types].xml": '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>',
        "_rels/.rels": '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
        "xl/workbook.xml": '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Asistencias" sheetId="1" r:id="rId1"/></sheets></workbook>',
        "xl/_rels/workbook.xml.rels": '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>',
        "xl/styles.xml": '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF2E60D3"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>',
        "xl/worksheets/sheet1.xml": sheet,
    }
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, value in files.items():
            archive.writestr(name, value.encode("utf-8"))
    return output.getvalue()


def pdf_bytes(rows, hospital="Hospital Félix Torrealva", generated=""):
    def literal(text):
        return str(text).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\n", " ").replace("\r", " ")

    def shorten(text, count):
        text = str(text)
        return text if len(text) <= count else text[:count - 3] + "..."

    objects = [b"", b"", b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>", b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"]
    pages = []
    chunks = [rows[i:i + 24] for i in range(0, len(rows), 24)] or [[]]
    for page_number, chunk in enumerate(chunks, 1):
        commands = []
        def text(x, y, value, size=9, bold=False):
            commands.append(f'BT /F{2 if bold else 1} {size} Tf {x} {y} Td ({literal(value)}) Tj ET')
        text(30, 558, "BIOTRACK", 19, True)
        text(30, 539, hospital, 11)
        text(30, 516, "Reporte de asistencia", 14, True)
        text(30, 500, f"Generado: {generated}     Registros: {len(rows)}", 9)
        commands.append("0.91 0.94 0.98 rg 30 464 782 22 re f 0 0 0 rg")
        columns = [(35, "Fecha"), (100, "Hora"), (153, "Personal"), (327, "Cargo"), (452, "Acción"), (572, "Origen"), (650, "Observación")]
        for x, label in columns:
            text(x, 472, label, 9, True)
        for index, row in enumerate(chunk):
            y = 446 - index * 16
            values = [row["fecha"], row["hora"], shorten(row["nombre"] + " " + row["apellido"], 31), shorten(row["cargo"], 22), row["accion_texto"], row["origen"], shorten(row.get("observacion", ""), 29)]
            for (x, _), value in zip(columns, values):
                text(x, y, value, 8)
            commands.append(f"0.85 G 30 {y - 5} m 812 {y - 5} l S 0 G")
        if not chunk:
            text(35, 440, "No se encontraron registros para los filtros seleccionados.")
        text(30, 26, "Los registros con origen demostracion corresponden a datos ficticios de ejemplo.", 8)
        text(715, 26, f"Página {page_number} de {len(chunks)}", 8)
        content = "\n".join(commands).encode("cp1252", errors="replace")
        page_id, stream_id = len(objects) + 1, len(objects) + 2
        objects.append(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents {stream_id} 0 R >>'.encode())
        objects.append(b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"\nendstream")
        pages.append(page_id)
    objects[0] = b"<< /Type /Catalog /Pages 2 0 R >>"
    objects[1] = (f'<< /Type /Pages /Count {len(pages)} /Kids [' + " ".join(f"{p} 0 R" for p in pages) + "] >>").encode()
    result = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for number, obj in enumerate(objects, 1):
        offsets.append(len(result))
        result += f"{number} 0 obj\n".encode() + obj + b"\nendobj\n"
    xref = len(result)
    result += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    result += b"".join(f"{offset:010d} 00000 n \n".encode() for offset in offsets[1:])
    result += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(result)


def export(rows, kind, hospital="Hospital Félix Torrealva", generated=""):
    if kind == "csv":
        return csv_bytes(rows), "text/csv; charset=utf-8"
    if kind == "xlsx":
        return xlsx_bytes(rows), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    if kind == "pdf":
        return pdf_bytes(rows, hospital, generated), "application/pdf"
    from .service import AppError
    raise AppError("Formato de reporte no admitido.")
