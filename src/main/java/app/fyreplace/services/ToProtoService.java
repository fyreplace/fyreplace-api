package app.fyreplace.services;

import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.EntityBase;
import app.fyreplace.protos.Chapter;
import app.fyreplace.protos.Comment;
import app.fyreplace.protos.Comments;
import app.fyreplace.protos.Cursor;
import app.fyreplace.protos.Id;
import app.fyreplace.protos.Image;
import app.fyreplace.protos.KeyValuePair;
import app.fyreplace.protos.Notification;
import app.fyreplace.protos.NotificationCount;
import app.fyreplace.protos.Notifications;
import app.fyreplace.protos.Post;
import app.fyreplace.protos.Posts;
import app.fyreplace.protos.Profile;
import app.fyreplace.protos.Profiles;
import app.fyreplace.protos.Rank;
import app.fyreplace.protos.Token;
import app.fyreplace.protos.User;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;

@RequestScoped
public final class ToProtoService {
    @Inject
    Block.Repository blockRepository;

    @Inject
    app.fyreplace.data.entities.Notification.Repository notificationRepository;

    @Inject
    app.fyreplace.data.entities.Post.Repository postRepository;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImageService imageService;

    @Inject
    StorageService storageService;

    public @Nonnull ByteString byteString(@Nonnull UUID uuid) {
        final var buffer = ByteBuffer.wrap(new byte[16])
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits());
        buffer.flip();
        return ByteString.copyFrom(buffer);
    }

    public @Nonnull Id id(@Nonnull UUID uuid) {
        return Id.newBuilder().setId(byteString(uuid)).build();
    }

    public @Nonnull Timestamp timestamp(@Nonnull OffsetDateTime offsetDateTime) {
        return Timestamp.newBuilder()
                .setSeconds(offsetDateTime.toEpochSecond())
                .setNanos(offsetDateTime.getNano())
                .build();
    }

    public @Nonnull Token token(@Nonnull String string) {
        return Token.newBuilder().setToken(string).build();
    }

    public @Nonnull Uni<Image> image(
            @Nullable app.fyreplace.data.entities.StoredFile storedFile, @Nullable byte[] data) {
        final var builder = Image.newBuilder();

        if (storedFile != null) {
            builder.setUrl(storageService.getUri(storedFile.getPath()).toString());
        }

        if (data == null || data.length == 0) {
            return Uni.createFrom().item(builder.build());
        }

        return Uni.createFrom()
                .item(builder)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .invoke(b -> b.setWidth(imageService.getWidth(data)).setHeight(imageService.getHeight(data)))
                .emitOn(Infrastructure.getDefaultExecutor())
                .map(Image.Builder::build);
    }

    public @Nonnull Rank rank(@Nonnull app.fyreplace.data.entities.User.Rank rank) {
        return switch (rank) {
            case app.fyreplace.data.entities.User.Rank.CITIZEN -> Rank.RANK_CITIZEN;
            case app.fyreplace.data.entities.User.Rank.STAFF -> Rank.RANK_STAFF;
            case app.fyreplace.data.entities.User.Rank.ADMIN -> Rank.RANK_SUPERUSER;
        };
    }

    public @Nonnull Uni<User> user(@Nonnull app.fyreplace.data.entities.User user) {
        final var callerId = currentUserService.getUserIdOrNull();
        final var isSelf = callerId != null && callerId.equals(user.getId());
        final var isPermanentlyBanned = user.isBanned() && user.getDateBanEnd() == null;
        final var builder = User.newBuilder().setDateJoined(timestamp(user.getDateCreated()));

        if (isPermanentlyBanned) {
            return profile(user).map(builder::setProfile).map(User.Builder::build);
        }

        if (isSelf) {
            builder.setEmail(Objects.requireNonNullElse(user.getEmail(), ""));
            builder.setBio(user.getBio());
        }

        return Uni.createFrom()
                .item(builder)
                .call(b -> profile(user).invoke(b::setProfile))
                .call(b -> isSelf
                        ? blockRepository
                                .countByIssuerId(user.getId())
                                .map(Long::intValue)
                                .invoke(b::setBlockedUsers)
                        : Uni.createFrom().voidItem())
                .map(User.Builder::build);
    }

    public @Nonnull Uni<Profiles> profiles(@Nonnull List<Block> list, @Nonnull Page page) {
        return bundle(
                list.stream().map(Block::getTarget).toList(),
                page,
                this::profile,
                Profiles.newBuilder()::addAllProfiles,
                Profiles.Builder::build,
                blockRepository::countByIssuerId,
                Profiles.Builder::setPrevious,
                Profiles.Builder::setNext);
    }

    public @Nonnull Uni<Profile> profile(@Nonnull app.fyreplace.data.entities.User user) {
        final var callerId = currentUserService.getUserIdOrNull();
        final var isPermanentlyBanned = user.isBanned() && user.getDateBanEnd() == null;

        if (isPermanentlyBanned) {
            return Uni.createFrom()
                    .item(Profile.newBuilder().setId(byteString(user.getId())).setIsBanned(true))
                    .map(Profile.Builder::build);
        }

        final var builder = Profile.newBuilder()
                .setId(byteString(user.getId()))
                .setRank(rank(user.getRank()))
                .setIsDeleted(user.isDeleted())
                .setIsBanned(user.isBanned())
                .setUsername(Objects.requireNonNullElse(user.getUsername(), ""));

        return Uni.createFrom()
                .item(builder)
                .call(b -> image(user.getAvatar(), null).invoke(b::setAvatar))
                .call(b -> callerId != null
                        ? blockRepository.isBlocking(callerId, user.getId()).invoke(b::setIsBlocked)
                        : Uni.createFrom().voidItem())
                .map(Profile.Builder::build);
    }

    public @Nonnull Uni<Post> post(@Nonnull app.fyreplace.data.entities.Post post) {
        return post(post, false);
    }

    public @Nonnull Uni<Post> post(@Nonnull app.fyreplace.data.entities.Post post, boolean preview) {
        final var callerId = currentUserService.getUserIdOrNull();
        final var chapters = preview ? post.getChapters().stream().limit(1) : post.getChapters().stream();
        return Uni.createFrom()
                .item(Post.newBuilder()
                        .setId(byteString(post.getId()))
                        .setDateCreated(timestamp(post.getDateCreated()))
                        .setIsAnonymous(post.isAnonymous())
                        .setIsPreview(preview)
                        .setChapterCount(post.getChapters().size())
                        .setCommentCount((int) post.getCommentCount())
                        .setVoteCount((int) post.getVoteCount()))
                .call(builder ->
                        authorOrNull(post, callerId).onItem().ifNotNull().invoke(builder::setAuthor))
                .call(builder -> applySubscriptionState(builder, post, callerId))
                .call(builder ->
                        allSequential(chapters.map(this::chapter).toList()).invoke(builder::addAllChapters))
                .map(Post.Builder::build);
    }

    public @Nonnull Uni<Post> post(@Nonnull app.fyreplace.data.entities.Notification notification) {
        final var post = notification.getPost();
        return post(post, true)
                .map(builder -> builder.toBuilder()
                        .setCommentsRead((int) (post.getCommentCount() - notification.getUnreadCommentCount()))
                        .build());
    }

    public @Nonnull Uni<Posts> posts(
            @Nonnull List<app.fyreplace.data.entities.Post> list, @Nonnull Page page, boolean published) {
        return bundle(
                list,
                page,
                p -> post(p, true),
                Posts.newBuilder()::addAllPosts,
                Posts.Builder::build,
                userId -> postRepository.countByAuthorId(userId, published),
                Posts.Builder::setPrevious,
                Posts.Builder::setNext);
    }

    public @Nonnull Uni<Posts> notificationPosts(
            @Nonnull List<app.fyreplace.data.entities.Notification> list, @Nonnull Page page) {
        return bundle(
                list,
                page,
                this::post,
                Posts.newBuilder()::addAllPosts,
                Posts.Builder::build,
                notificationRepository::countByUserId,
                Posts.Builder::setPrevious,
                Posts.Builder::setNext);
    }

    public @Nonnull Uni<Chapter> chapter(@Nonnull app.fyreplace.data.entities.Chapter chapter) {
        return Uni.createFrom()
                .item(Chapter.newBuilder().setText(chapter.getText()).setIsTitle(false))
                .call(builder -> image(chapter.getImage(), null)
                        .invoke(image -> builder.setImage(
                                chapter.getImage() == null
                                        ? image
                                        : image.toBuilder()
                                                .setWidth(chapter.getWidth())
                                                .setHeight(chapter.getHeight())
                                                .build())))
                .map(Chapter.Builder::build);
    }

    public @Nonnull Uni<Comment> comment(@Nonnull app.fyreplace.data.entities.Comment comment) {
        return Uni.createFrom()
                .item(Comment.newBuilder()
                        .setId(byteString(comment.getId()))
                        .setDateCreated(timestamp(comment.getDateCreated()))
                        .setIsDeleted(comment.isDeleted())
                        .setText(comment.getText()))
                .call(builder -> profile(comment.getAuthor()).invoke(builder::setAuthor))
                .map(Comment.Builder::build);
    }

    public @Nonnull Uni<Comments> comments(
            @Nonnull List<app.fyreplace.data.entities.Comment> list, @Nonnull Page page) {
        return prepareBundleBuilder(
                        list,
                        this::comment,
                        comments -> Comments.newBuilder()
                                .addAllComments(IntStream.range(0, comments.size())
                                        .mapToObj(i -> comments.get(i).toBuilder()
                                                .setPosition(page.index * page.size + i)
                                                .build())
                                        .toList()))
                .map(Comments.Builder::build);
    }

    public @Nonnull Uni<Notification> notification(@Nonnull app.fyreplace.data.entities.Notification notification) {
        return Uni.createFrom()
                .item(Notification.newBuilder()
                        .setCount((int) notification.getUnreadCommentCount())
                        .setIsFlag(false))
                .call(builder -> post(notification).invoke(builder::setPost))
                .map(Notification.Builder::build);
    }

    public @Nonnull Uni<Notifications> notifications(
            @Nonnull List<app.fyreplace.data.entities.Notification> list, @Nonnull Page page) {
        return bundle(
                list,
                page,
                this::notification,
                Notifications.newBuilder()::addAllNotifications,
                Notifications.Builder::build,
                notificationRepository::countByUserId,
                Notifications.Builder::setPrevious,
                Notifications.Builder::setNext);
    }

    public @Nonnull NotificationCount notificationCount(long count) {
        return NotificationCount.newBuilder().setCount((int) count).build();
    }

    public @Nonnull Cursor cursor(@Nonnull Page page) {
        return Cursor.newBuilder()
                .addData(KeyValuePair.newBuilder()
                        .setKey("index")
                        .setValue(String.valueOf(page.index))
                        .build())
                .addData(KeyValuePair.newBuilder()
                        .setKey("size")
                        .setValue(String.valueOf(page.size))
                        .build())
                .build();
    }

    private @Nonnull Uni<Profile> authorOrNull(
            @Nonnull app.fyreplace.data.entities.Post post, @Nullable UUID callerId) {
        return post.anonymous && !post.author.id.equals(callerId)
                ? Uni.createFrom().nullItem()
                : profile(post.author);
    }

    private @Nonnull Uni<Void> applySubscriptionState(
            @Nonnull Post.Builder builder, @Nonnull app.fyreplace.data.entities.Post post, @Nullable UUID callerId) {
        return callerId == null
                ? Uni.createFrom().voidItem()
                : notificationRepository
                        .findByUserIdAndPostIdOrNull(callerId, post.getId())
                        .onItem()
                        .ifNotNull()
                        .invoke(notification -> builder.setIsSubscribed(true)
                                .setCommentsRead((int) (post.getCommentCount() - notification.getUnreadCommentCount())))
                        .replaceWithVoid();
    }

    private <E extends EntityBase, I extends Message, B extends Message.Builder, M extends Message> @Nonnull
            Uni<M> bundle(
                    @Nonnull List<E> list,
                    @Nonnull Page page,
                    @Nonnull Function<E, Uni<I>> toProto,
                    @Nonnull Function<List<I>, B> toBuilder,
                    @Nonnull Function<B, M> build,
                    @Nonnull Function<UUID, Uni<Long>> counter,
                    @Nonnull BiConsumer<B, Cursor> setPrevious,
                    @Nonnull BiConsumer<B, Cursor> setNext) {
        return prepareBundleBuilder(list, toProto, toBuilder)
                .call(builder -> addBundleCursors(page, counter, builder, setPrevious, setNext))
                .map(build);
    }

    private <E extends EntityBase, I extends Message, B extends Message.Builder> @Nonnull Uni<B> prepareBundleBuilder(
            @Nonnull List<E> list, @Nonnull Function<E, Uni<I>> toProto, @Nonnull Function<List<I>, B> toBuilder) {
        return allSequential(list.stream().map(toProto).toList()).map(toBuilder);
    }

    private static @Nonnull <T> Uni<List<T>> allSequential(@Nonnull List<Uni<T>> unis) {
        return Multi.createFrom()
                .iterable(unis)
                .concatMap(Uni::toMulti)
                .collect()
                .asList();
    }

    private <B> @Nonnull Uni<Void> addBundleCursors(
            @Nonnull Page page,
            @Nonnull Function<UUID, Uni<Long>> counter,
            @Nonnull B builder,
            @Nonnull BiConsumer<B, Cursor> setPrevious,
            @Nonnull BiConsumer<B, Cursor> setNext) {
        return Uni.createFrom()
                .item(currentUserService::getUserId)
                .flatMap(counter::apply)
                .invoke(count -> setBundleCursors(page, count, builder, setPrevious, setNext))
                .replaceWithVoid();
    }

    private <B> void setBundleCursors(
            @Nonnull Page page,
            long count,
            @Nonnull B builder,
            @Nonnull BiConsumer<B, Cursor> setPrevious,
            @Nonnull BiConsumer<B, Cursor> setNext) {
        if (page.index > 0) {
            setPrevious.accept(builder, cursor(page.previous()));
        }

        if ((long) (page.index + 1) * page.size < count) {
            setNext.accept(builder, cursor(page.next()));
        }
    }
}
