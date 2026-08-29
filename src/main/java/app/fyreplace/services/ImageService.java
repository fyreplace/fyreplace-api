package app.fyreplace.services;

import app.fyreplace.services.mimetype.KnownFileType;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.runtime.configuration.MemorySize;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javaxt.io.Image;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public final class ImageService {
    @ConfigProperty(name = "app.storage.limits.max-size")
    MemorySize fileMaxSize;

    public boolean isValidImage(@Nonnull byte[] data) {
        try {
            final var mimeType = getMimeType(data);
            return Arrays.stream(KnownFileType.values()).anyMatch(m -> m.mime.equals(mimeType));
        } catch (Exception e) {
            return false;
        }
    }

    public @Nonnull String getMimeType(@Nonnull byte[] data) {
        try {
            final var reader = getFirstValidReader(data);
            final var format = reader.getFormatName().toUpperCase();
            return Arrays.stream(KnownFileType.values())
                    .filter(m -> m.name().equals(format))
                    .findFirst()
                    .orElseThrow()
                    .mime;
        } catch (IOException | NoSuchElementException e) {
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT.withCause(e));
        }
    }

    public int getWidth(@Nonnull byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data)).getWidth();
        } catch (IOException e) {
            return 0;
        }
    }

    public int getHeight(@Nonnull byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data)).getHeight();
        } catch (IOException e) {
            return 0;
        }
    }

    public @Nonnull Uni<byte[]> collectImage(@Nonnull Multi<ByteString> requests) {
        return requests.collect()
                .with(Collectors.reducing(ByteString.EMPTY, ByteString::concat))
                .map(ByteString::toByteArray)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .map(this::shrink)
                .emitOn(Infrastructure.getDefaultExecutor());
    }

    private byte[] shrink(@Nonnull byte[] data) {
        return shrink(data, 1.5);
    }

    private byte[] shrink(@Nonnull byte[] data, double scaleFactorIncrease) {
        final var maxSize = fileMaxSize.asLongValue();

        if (data.length <= maxSize) {
            return data;
        }

        final var inputImage = new Image(data);
        final var scaleFactor = Math.sqrt((double) data.length / maxSize) * scaleFactorIncrease;
        inputImage.resize((int) (inputImage.getWidth() / scaleFactor), (int) (inputImage.getHeight() / scaleFactor));
        inputImage.rotate();
        final var output = inputImage.getByteArray();

        if (output.length <= maxSize) {
            return output;
        } else if (scaleFactorIncrease >= 3) {
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        } else {
            return shrink(data, scaleFactorIncrease + 0.5);
        }
    }

    private @Nonnull ImageReader getFirstValidReader(@Nonnull byte[] data) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            final var readers = ImageIO.getImageReaders(input);

            while (readers.hasNext()) {
                final var reader = readers.next();
                final var format = reader.getFormatName().toUpperCase();

                if (Arrays.stream(KnownFileType.values()).anyMatch(m -> m.name().equals(format))) {
                    return reader;
                }
            }

            throw new IOException();
        }
    }
}
