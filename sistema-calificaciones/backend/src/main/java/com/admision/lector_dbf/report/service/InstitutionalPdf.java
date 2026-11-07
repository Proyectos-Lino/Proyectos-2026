package com.admision.lector_dbf.report.service;

import com.admision.lector_dbf.account.model.OrganizationProfile;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/** Identidad institucional compartida por todos los documentos del sistema. */
public final class InstitutionalPdf implements AutoCloseable {
    public static final Color INK=new Color(27,53,64), ACCENT=new Color(24,100,87), LIGHT=new Color(240,245,243);
    private final ByteArrayOutputStream output=new ByteArrayOutputStream();
    private final Document document=new Document(PageSize.A4,36,36,175,65);

    public InstitutionalPdf(OrganizationProfile organization,byte[] logo,String title,String subtitle,String reference) {
        PdfWriter writer=PdfWriter.getInstance(document,output);
        document.addTitle(title); document.addAuthor(organization.name());
        PageBrand brand=new PageBrand(organization,logo,title,subtitle,reference);
        document.setMargins(36,36,Math.max(155,brand.header().getTotalHeight()+77),65);
        writer.setPageEvent(brand);
        document.open();
    }
    public Document document() { return document; }
    public byte[] finish() { if (document.isOpen()) document.close(); return output.toByteArray(); }
    @Override public void close() { if (document.isOpen()) document.close(); }
    public static Font font(float size,boolean bold) { return new Font(Font.HELVETICA,size,bold ? Font.BOLD : Font.NORMAL,INK); }
    public static String text(String value) { return value==null || value.isBlank() ? "-" : value.replaceAll("[\\p{Cntrl}]"," ").replace('\u2011','-'); }
    public static PdfPCell cell(String value,boolean heading) {
        PdfPCell cell=new PdfPCell(new Phrase(text(value),font(heading ? 8.5f : 8,heading)));
        cell.setPadding(6); cell.setBorderColor(new Color(219,229,225)); cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (heading) cell.setBackgroundColor(LIGHT);
        return cell;
    }
    public static PdfPTable table(float... widths) {
        PdfPTable table=new PdfPTable(widths); table.setWidthPercentage(100); table.setSpacingBefore(10); table.setSpacingAfter(12);
        table.setSplitLate(false); return table;
    }
    public static void signature(Document document,OrganizationProfile organization) {
        if (organization.signatoryName()==null || organization.signatoryName().isBlank()) return;
        Paragraph name=new Paragraph(organization.signatoryName(),font(10,true)); name.setSpacingBefore(28); name.setAlignment(Element.ALIGN_RIGHT);
        document.add(name);
        if (organization.signatoryTitle()!=null && !organization.signatoryTitle().isBlank()) {
            Paragraph role=new Paragraph(organization.signatoryTitle(),font(9,false)); role.setAlignment(Element.ALIGN_RIGHT); document.add(role);
        }
    }

    private static final class PageBrand extends PdfPageEventHelper {
        private final OrganizationProfile org;
        private final byte[] logo;
        private final String title,subtitle,reference;
        private final String timestamp=OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm xxx"));
        private PdfTemplate total;
        private BaseFont base;
        PageBrand(OrganizationProfile org,byte[] logo,String title,String subtitle,String reference) {
            this.org=org; this.logo=logo; this.title=title; this.subtitle=subtitle; this.reference=reference;
        }
        @Override public void onOpenDocument(PdfWriter writer,Document document) {
            total=writer.getDirectContent().createTemplate(25,12);
            try { base=BaseFont.createFont(BaseFont.HELVETICA,BaseFont.WINANSI,false); }
            catch (Exception exception) { throw new IllegalStateException("No se pudo cargar la fuente PDF",exception); }
        }
        private PdfPTable header() {
            PdfPTable header=new PdfPTable(logo==null ? new float[]{1} : new float[]{76,440});
            header.setTotalWidth(PageSize.A4.getWidth()-72);
            if (logo!=null) {
                try {
                    Image image=Image.getInstance(logo); image.scaleToFit(64,72);
                    PdfPCell mark=new PdfPCell(image,false); mark.setBorder(Rectangle.NO_BORDER); mark.setPaddingRight(12); header.addCell(mark);
                } catch (Exception exception) { throw new IllegalStateException("No se pudo dibujar el logo",exception); }
            }
            PdfPCell identity=new PdfPCell(); identity.setBorder(Rectangle.NO_BORDER);
            identity.addElement(new Paragraph(text(org.legalName()==null ? org.name() : org.legalName()),font(13,true)));
            if (org.office()!=null) identity.addElement(new Paragraph(org.office(),font(9,true)));
            String location=join(org.address(),join(org.city(),org.country()));
            if (!location.isEmpty()) identity.addElement(new Paragraph(location,font(8,false)));
            String contact=join(org.email(),join(org.phone(),org.website()));
            if (!contact.isEmpty()) identity.addElement(new Paragraph(contact,font(8,false)));
            if (org.taxId()!=null) identity.addElement(new Paragraph("Identificación fiscal: "+org.taxId(),font(8,false)));
            header.addCell(identity);
            header.calculateHeights(true);
            return header;
        }
        @Override public void onEndPage(PdfWriter writer,Document document) {
            PdfPTable header=header();
            header.writeSelectedRows(0,-1,document.left(),document.getPageSize().getHeight()-27,writer.getDirectContent());
            PdfContentByte canvas=writer.getDirectContent();
            float ruleY=document.top()+38;
            canvas.setColorStroke(ACCENT); canvas.setLineWidth(1); canvas.moveTo(document.left(),ruleY); canvas.lineTo(document.right(),ruleY); canvas.stroke();
            ColumnText.showTextAligned(canvas,Element.ALIGN_LEFT,new Phrase(title.toUpperCase(java.util.Locale.ROOT),font(10,true)),document.left(),ruleY-17,0);
            ColumnText.showTextAligned(canvas,Element.ALIGN_LEFT,new Phrase(subtitle,font(8,false)),document.left(),ruleY-30,0);
            canvas.setColorStroke(new Color(219,229,225)); canvas.moveTo(document.left(),48); canvas.lineTo(document.right(),48); canvas.stroke();
            ColumnText.showTextAligned(canvas,Element.ALIGN_LEFT,new Phrase(reference+" | "+timestamp,font(7,false)),document.left(),34,0);
            String page="Página "+writer.getPageNumber()+" de ";
            ColumnText.showTextAligned(canvas,Element.ALIGN_LEFT,new Phrase(page,font(8,false)),document.right()-95,34,0);
            canvas.addTemplate(total,document.right()-95+base.getWidthPoint(page,8)+2,34);
        }
        @Override public void onCloseDocument(PdfWriter writer,Document document) {
            total.beginText(); total.setFontAndSize(base,8); total.setTextMatrix(0,0); total.showText(Integer.toString(writer.getPageNumber()-1)); total.endText();
        }
        private String join(String first,String second) {
            if (first==null || first.isBlank()) return second==null ? "" : second;
            return second==null || second.isBlank() ? first : first+" | "+second;
        }
    }
}
