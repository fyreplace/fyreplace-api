package app.fyreplace.services;

import app.fyreplace.errors.InternalException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

@ApplicationScoped
public final class QrCodeService {
    private static final int QR_CODE_SIZE = 400;

    public @Nonnull String encodeToBase64Png(@Nonnull String content) {
        try {
            final var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
            final var buffer = new ByteArrayOutputStream();
            ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "PNG", buffer);
            return Base64.getEncoder().encodeToString(buffer.toByteArray());
        } catch (WriterException | IOException e) {
            throw new InternalException(e);
        }
    }
}
