package app.fyreplace.services.storage.s3;

import app.fyreplace.errors.InternalException;
import app.fyreplace.services.ImageService;
import app.fyreplace.services.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@ApplicationScoped
@Unremovable
@IfBuildProperty(name = "app.storage.type", stringValue = "s3")
public final class S3StorageService implements StorageService {
    private static final Logger LOG = Logger.getLogger(S3StorageService.class);

    @Inject
    S3StorageConfig config;

    @Inject
    S3AsyncClient client;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ImageService imageService;

    public void onStartup(@Nonnull @Observes StartupEvent event) {
        Uni.createFrom()
                .completionStage(client.putBucketPolicy(b -> {
                    final var statement = new Policy.Statement(
                            "Allow", "*", "s3:GetObject", "arn:aws:s3:::" + config.bucket() + "/*");

                    try {
                        b.bucket(config.bucket())
                                .policy(objectMapper.writeValueAsString(
                                        new Policy(Policy.CURRENT_VERSION, List.of(statement))));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .subscribe()
                .with(_ -> {}, failure -> LOG.error("Failed to apply the S3 bucket policy", failure));
    }

    @Override
    public @Nonnull Uni<byte[]> fetch(@Nonnull String path) {
        return Uni.createFrom()
                .completionStage(
                        client.getObject(b -> b.bucket(config.bucket()).key(path), AsyncResponseTransformer.toBytes()))
                .map(BytesWrapper::asByteArray);
    }

    @Override
    public @Nonnull Uni<Void> store(@Nonnull String path, @Nonnull byte[] data) {
        return Uni.createFrom()
                .completionStage(client.putObject(
                        b -> b.bucket(config.bucket()).key(path).contentType(imageService.getMimeType(data)),
                        AsyncRequestBody.fromBytes(data)))
                .replaceWithVoid();
    }

    @Override
    public @Nonnull Uni<Void> remove(@Nonnull String path) {
        return Uni.createFrom()
                .completionStage(
                        client.deleteObject(b -> b.bucket(config.bucket()).key(path)))
                .replaceWithVoid();
    }

    @Override
    public @Nonnull URI getUri(@Nonnull String path) {
        try {
            return client.utilities()
                    .getUrl(b -> {
                        b.bucket(config.bucket()).key(path);
                        config.customEndpoint().ifPresent(b::endpoint);
                    })
                    .toURI();
        } catch (URISyntaxException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public @Nonnull String getPath(@Nonnull URI uri) {
        return uri.getPath();
    }
}
