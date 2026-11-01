package app.fyreplace.grpc.endpoints.chapter;

import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.StoredFile;
import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.GrpcTestsBase;
import app.fyreplace.protos.ChapterImageUpdate;
import app.fyreplace.protos.ChapterLocation;
import app.fyreplace.protos.ChapterRelocation;
import app.fyreplace.protos.ChapterTextUpdate;
import app.fyreplace.protos.ImageChunk;
import app.fyreplace.protos.MutinyChapterServiceGrpc;
import app.fyreplace.protos.MutinyPostServiceGrpc;
import app.fyreplace.services.ToProtoService;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class ChapterTestsBase extends GrpcTestsBase {
    protected static final String ACTIVE_USER = "user_0";
    protected static final String OTHER_USER = "user_1";

    @Inject
    protected Chapter.Repository chapterRepository;

    @Inject
    protected Post.Repository postRepository;

    @Inject
    protected StoredFile.Repository storedFileRepository;

    @Inject
    protected ToProtoService toProtoService;

    private List<UUID> seededChapterIds;
    private List<UUID> seededPostIds;
    private List<UUID> seededFileIds;

    @BeforeEach
    void setup() {
        final var chaptersRef = new AtomicReference<List<Chapter>>();
        final var postsRef = new AtomicReference<List<Post>>();
        final var filesRef = new AtomicReference<List<StoredFile>>();

        executorService.awaitOnSafeContext(() -> chapterRepository
                .findAll()
                .list()
                .invoke(chaptersRef::set)
                .replaceWith(postRepository::findAll)
                .flatMap(PanacheQuery::list)
                .invoke(postsRef::set)
                .replaceWith(storedFileRepository::findAll)
                .flatMap(PanacheQuery::list)
                .invoke(filesRef::set));

        seededChapterIds = chaptersRef.get().stream().map(Chapter::getId).toList();
        seededPostIds = postsRef.get().stream().map(Post::getId).toList();
        seededFileIds = filesRef.get().stream().map(StoredFile::getId).toList();
    }

    @AfterEach
    void teardown() {
        executorService.awaitOnSafeContext(() -> Uni.createFrom()
                .voidItem()
                .chain(() -> chapterRepository.deleteByIdNotIn(seededChapterIds))
                .chain(() -> postRepository.deleteByIdNotIn(seededPostIds))
                .chain(() -> storedFileRepository.deleteByIdNotIn(seededFileIds)));
    }

    protected final @Nonnull MutinyChapterServiceGrpc.MutinyChapterServiceStub chapterService() {
        return MutinyChapterServiceGrpc.newMutinyStub(channel());
    }

    protected final @Nonnull MutinyChapterServiceGrpc.MutinyChapterServiceStub chapterService(@Nullable String token) {
        return withToken(chapterService(), token);
    }

    protected final @Nonnull MutinyPostServiceGrpc.MutinyPostServiceStub postService(@Nullable String token) {
        return withToken(MutinyPostServiceGrpc.newMutinyStub(channel()), token);
    }

    protected final @Nonnull Uni<UUID> newDraft(@Nonnull User author) {
        return postService(token(author)).create(Empty.getDefaultInstance()).map(this::uuid);
    }

    protected final @Nonnull Uni<UUID> draftWithChapters(@Nonnull User author, int chapterCount) {
        final var service = chapterService(token(author));
        return newDraft(author)
                .flatMap(postId -> chainAll(IntStream.range(0, chapterCount)
                                .mapToObj(position ->
                                        (Supplier<Uni<Void>>) () -> service.create(location(postId, position))
                                                .call(() -> service.updateText(
                                                        textUpdate(postId, position, "chapter_" + position)))
                                                .replaceWithVoid())
                                .toList())
                        .replaceWith(postId));
    }

    protected final @Nonnull Uni<Post> postOf(@Nonnull String username, boolean published, int position) {
        return user(username)
                .flatMap(u -> session(() -> postRepository
                        .findByAuthorId(u.getId(), published, false)
                        .list()))
                .map(posts -> posts.get(position));
    }

    protected final @Nonnull Uni<List<Chapter>> chaptersOf(@Nonnull UUID postId) {
        return session(() -> chapterRepository.findByPostId(postId).list());
    }

    protected final @Nonnull Uni<Chapter> chapterAt(@Nonnull UUID postId, int position) {
        return session(() -> chapterRepository
                .findByLocation(new Chapter.Location(postId, position))
                .singleResult());
    }

    protected final @Nonnull ChapterLocation location(@Nonnull UUID postId, int position) {
        return ChapterLocation.newBuilder()
                .setPostId(toProtoService.byteString(postId))
                .setPosition(position)
                .build();
    }

    protected final @Nonnull ChapterRelocation relocation(@Nonnull UUID postId, int fromPosition, int toPosition) {
        return ChapterRelocation.newBuilder()
                .setPostId(toProtoService.byteString(postId))
                .setFromPosition(fromPosition)
                .setToPosition(toPosition)
                .build();
    }

    protected final @Nonnull ChapterTextUpdate textUpdate(@Nonnull UUID postId, int position, @Nonnull String text) {
        return ChapterTextUpdate.newBuilder()
                .setLocation(location(postId, position))
                .setText(text)
                .build();
    }

    protected final @Nonnull Multi<ChapterImageUpdate> pngUpload(@Nonnull UUID postId, int position) {
        final var data = pngBytes();
        final var half = data.length / 2;
        return Multi.createFrom()
                .items(
                        ChapterImageUpdate.newBuilder()
                                .setLocation(location(postId, position))
                                .build(),
                        chunk(data, 0, half),
                        chunk(data, half, data.length - half));
    }

    protected final @Nonnull Multi<ChapterImageUpdate> locationOnlyUpload(@Nonnull UUID postId, int position) {
        return Multi.createFrom()
                .item(ChapterImageUpdate.newBuilder()
                        .setLocation(location(postId, position))
                        .build());
    }

    protected final @Nonnull Multi<ChapterImageUpdate> textUpload(@Nonnull UUID postId, int position) {
        return Multi.createFrom()
                .items(
                        ChapterImageUpdate.newBuilder()
                                .setLocation(location(postId, position))
                                .build(),
                        ChapterImageUpdate.newBuilder()
                                .setChunk(ImageChunk.newBuilder()
                                        .setData(ByteString.copyFromUtf8("not an image"))
                                        .build())
                                .build());
    }

    private static @Nonnull ChapterImageUpdate chunk(byte[] data, int offset, int length) {
        return ChapterImageUpdate.newBuilder()
                .setChunk(ImageChunk.newBuilder().setData(ByteString.copyFrom(data, offset, length)))
                .build();
    }

    private static byte[] pngBytes() {
        try {
            final var image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            final var baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create test PNG", e);
        }
    }
}
