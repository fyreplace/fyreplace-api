package app.fyreplace.grpc.endpoints;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

public final class JwtTestsResource implements QuarkusTestResourceLifecycleManager {
    @Override
    public Map<String, String> start() {
        try {
            final var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            final var keyPair = generator.generateKeyPair();

            final var directory = Files.createTempDirectory("fyreplace-jwt");
            final var privateKey = directory.resolve("jwt.rsa.pem");
            final var publicKey = directory.resolve("jwt.rsa.pub");

            Files.writeString(
                    privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));

            return Map.of(
                    "smallrye.jwt.sign.key.location", privateKey.toString(),
                    "mp.jwt.verify.publickey.location", publicKey.toString());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Could not generate the test RSA keypair", e);
        }
    }

    @Override
    public void stop() {}

    private static @Nonnull String pem(@Nonnull String type, @Nonnull byte[] contents) {
        final var encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(contents);
        return "-----BEGIN %s-----%n%s%n-----END %s-----%n".formatted(type, encoded, type);
    }
}
