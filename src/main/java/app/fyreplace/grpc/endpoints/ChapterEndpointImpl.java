package app.fyreplace.grpc.endpoints;

import app.fyreplace.data.entities.Post;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.protos.ChapterImageUpdate;
import app.fyreplace.protos.ChapterLocation;
import app.fyreplace.protos.ChapterRelocation;
import app.fyreplace.protos.ChapterService;
import app.fyreplace.protos.ChapterTextUpdate;
import app.fyreplace.protos.Image;
import app.fyreplace.protos.ImageChunk;
import app.fyreplace.services.AuthorizationService;
import app.fyreplace.services.ImageService;
import app.fyreplace.services.ValidationService;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.util.UUID;

@GrpcService
public final class ChapterEndpointImpl extends EndpointBase implements ChapterService {
    @Inject
    AuthorizationService authorizationService;

    @Inject
    ImageService imageService;

    @Inject
    ValidationService validationService;

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> create(@Nonnull ChapterLocation request) {
        return Uni.createFrom()
                .item(fromProtoService.location(request))
                .flatMap(location -> findWritablePost(location.postId())
                        .call(post -> validationService.checkChapterInsertable(location.position(), post.getId()))
                        .replaceWith(location))
                .map(fromProtoService::newChapter)
                .flatMap(chapterRepository::persist)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> move(@Nonnull ChapterRelocation request) {
        return Uni.createFrom()
                .item(request)
                .map(r -> Tuple2.of(fromProtoService.location(r), r))
                .flatMap(pair -> findWritablePost(pair.getItem1().postId())
                        .call(post -> validationService.checkChapterRelocation(
                                pair.getItem2().getFromPosition(),
                                pair.getItem2().getToPosition(),
                                post.getId()))
                        .replaceWith(pair))
                .call(pair -> chapterRepository.moveByLocation(
                        pair.getItem1(), pair.getItem2().getToPosition()))
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> updateText(@Nonnull ChapterTextUpdate request) {
        return Uni.createFrom()
                .item(request)
                .map(r -> Tuple2.of(fromProtoService.location(r.getLocation()), r))
                .flatMap(pair -> findWritablePost(pair.getItem1().postId())
                        .call(post -> validationService.checkChapterPosition(
                                pair.getItem1().position(), post.getId()))
                        .replaceWith(pair))
                .call(pair -> validationService.checkChapterText(pair.getItem2().getText()))
                .call(pair -> chapterRepository.updateTextByLocation(
                        pair.getItem1(), pair.getItem2().getText()))
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    public @Nonnull Uni<Image> updateImage(@Nonnull Multi<ChapterImageUpdate> request) {
        final var cached = request.cache();
        final var location = cached.filter(ChapterImageUpdate::hasLocation)
                .map(ChapterImageUpdate::getLocation)
                .collect()
                .first()
                .onItem()
                .ifNull()
                .failWith(() -> new InvalidArgumentException("missing_location"))
                .map(fromProtoService::location);
        final var imageData = cached.filter(ChapterImageUpdate::hasChunk)
                .map(ChapterImageUpdate::getChunk)
                .map(ImageChunk::getData);
        return location.flatMap(l -> imageService
                        .collectChapterImage(imageData)
                        .call(validationService::checkImage)
                        .flatMap(imageService::shrinkImage)
                        .flatMap(data -> imageService.dimensions(data).map(dims -> Tuple3.of(l, data, dims))))
                .emitOn(eventLoop())
                .flatMap(triple -> Panache.withTransaction(() -> findWritablePost(
                                triple.getItem1().postId())
                        .call(post -> validationService.checkChapterPosition(
                                triple.getItem1().position(), post.getId()))
                        .chain(() -> chapterRepository
                                .storeOrRemoveImageByLocationOrNull(
                                        triple.getItem1(),
                                        triple.getItem2(),
                                        triple.getItem3().width(),
                                        triple.getItem3().height())
                                .map(file -> Tuple2.of(file, triple.getItem2())))))
                .flatMap(pair -> toProtoService.image(pair.getItem1(), pair.getItem2()));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> delete(@Nonnull ChapterLocation request) {
        return Uni.createFrom()
                .item(request)
                .map(fromProtoService::location)
                .flatMap(location -> findWritablePost(location.postId())
                        .call(post -> validationService.checkChapterPosition(location.position(), post.getId()))
                        .replaceWith(location))
                .call(chapterRepository::deleteByLocation)
                .replaceWith(Empty::getDefaultInstance);
    }

    private @Nonnull Uni<Post> findWritablePost(@Nonnull UUID postId) {
        return postRepository
                .findReadableById(currentUserService.getUserId(), postId)
                .invoke(post -> authorizationService.checkPostWritable(currentUserService.getUserId(), post));
    }
}
