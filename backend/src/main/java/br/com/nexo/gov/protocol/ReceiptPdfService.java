package br.com.nexo.gov.protocol;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;

@Service
public class ReceiptPdfService {
    private final String publicBaseUrl;

    public ReceiptPdfService(@Value("${nexogov.public-base-url}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    public byte[] create(String number, String subject, OffsetDateTime createdAt, String token) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                write(content, bold, 20, 56, 770, "COMPROVANTE DE PROTOCOLO");
                write(content, regular, 12, 56, 730, "Número: " + number);
                write(content, regular, 12, 56, 704, "Recebido em: " + createdAt.toString());
                write(content, regular, 12, 56, 678, "Assunto: " + safe(subject, 88));
                write(content, regular, 10, 56, 625, "Guarde este comprovante para consultar o andamento do pedido.");
                write(content, regular, 10, 56, 607, "Aponte a câmera para o QR Code ou abra o portal e informe seu token.");
                String trackingUrl = publicBaseUrl + "/#/acompanhar?token=" + token;
                BitMatrix matrix = new MultiFormatWriter().encode(trackingUrl, BarcodeFormat.QR_CODE, 180, 180);
                BufferedImage qr = new BufferedImage(180, 180, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 180; y++) for (int x = 0; x < 180; x++) qr.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                content.drawImage(LosslessFactory.createFromImage(document, qr), 56, 390, 180, 180);
                write(content, regular, 9, 56, 370, "Consulta protegida por token individual. Não compartilhe este código.");
            }
            document.save(bytes);
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível gerar o comprovante PDF.", exception);
        }
    }

    private static void write(PDPageContentStream stream, PDType1Font font, int size, int x, int y, String text) throws Exception {
        stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, y); stream.showText(safe(text, 100)); stream.endText();
    }

    private static String safe(String text, int max) {
        String normalized = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max - 1) + "…";
    }
}
