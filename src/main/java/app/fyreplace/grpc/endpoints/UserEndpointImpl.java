package app.fyreplace.grpc.endpoints;

import app.fyreplace.mails.UserBannedMail;
import app.fyreplace.mails.UserEmailUpdateMail;
import app.fyreplace.protos.BanSentence;
import app.fyreplace.protos.Bio;
import app.fyreplace.protos.Block;
import app.fyreplace.protos.Email;
import app.fyreplace.protos.Id;
import app.fyreplace.protos.Image;
import app.fyreplace.protos.ImageChunk;
import app.fyreplace.protos.Page;
import app.fyreplace.protos.Profiles;
import app.fyreplace.protos.Promotion;
import app.fyreplace.protos.Rank;
import app.fyreplace.protos.Token;
import app.fyreplace.protos.User;
import app.fyreplace.protos.UserService;
import app.fyreplace.services.AuthorizationService;
import app.fyreplace.services.ImageService;
import app.fyreplace.services.ValidationService;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

@GrpcService
public final class UserEndpointImpl extends EndpointBase implements UserService {
    @Inject
    AuthorizationService authorizationService;

    @Inject
    ImageService imageService;

    @Inject
    UserBannedMail userBannedMail;

    @Inject
    UserEmailUpdateMail userEmailUpdateMail;

    @Inject
    ValidationService validationService;

    @Override
    @WithSession
    public @Nonnull Uni<User> retrieve(@Nonnull Id request) {
        return uuidOf(request::getId).flatMap(userRepository::findExistingById).flatMap(toProtoService::user);
    }

    @Override
    @Authenticated
    @WithSession
    public @Nonnull Uni<User> retrieveMe(@Nonnull Empty request) {
        return currentUserService.getUser().flatMap(toProtoService::user);
    }

    @Override
    @Authenticated
    public @Nonnull Uni<Image> updateAvatar(@Nonnull Multi<ImageChunk> request) {
        return Uni.createFrom()
                .item(request)
                .map(r -> r.map(ImageChunk::getData))
                .flatMap(imageService::collectImage)
                .call(validationService::checkImage)
                .flatMap(imageService::shrinkImage)
                .emitOn(eventLoop())
                .flatMap(data -> Panache.withTransaction(
                                () -> userRepository.storeOrRemoveAvatarById(currentUserService.getUserId(), data))
                        .map(file -> Tuple2.of(file, data)))
                .flatMap(pair -> toProtoService.image(pair.getItem1(), pair.getItem2()));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> updateBio(@Nonnull Bio request) {
        return Uni.createFrom()
                .item(request::getBio)
                .call(validationService::checkBio)
                .call(bio -> userRepository.updateBioByUserId(currentUserService.getUserId(), bio))
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithSession
    public @Nonnull Uni<Empty> sendEmailUpdateEmail(@Nonnull Email request) {
        return Uni.createFrom()
                .item(request)
                .call(r -> validationService.checkEmailUpdate(r.getEmail()))
                .flatMap(r -> currentUserService.getUser().map(user -> Tuple2.of(user, r.getEmail())))
                .call(pair -> userEmailUpdateMail.send(pair.getItem1(), pair.getItem2()))
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> confirmEmailUpdate(@Nonnull Token request) {
        return Uni.createFrom()
                .item(request::getToken)
                .flatMap(authorizationService::emailUpdate)
                .flatMap(emailUpdate -> currentUserService.getUser().map(caller -> Tuple2.of(emailUpdate, caller)))
                .invoke(pair -> authorizationService.checkEmailUpdateUser(
                        pair.getItem1().user(), pair.getItem2()))
                .call(pair -> userRepository.updateEmailById(
                        pair.getItem2().getId(), pair.getItem1().email()))
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    public @Nonnull Multi<Profiles> listBlocked(@Nonnull Multi<Page> request) {
        return paginate(
                request,
                (header, page) -> Panache.withSession(() -> blockRepository
                        .findByIssuerId(currentUserService.getUserId(), header.getForward())
                        .page(page)
                        .list()
                        .flatMap(items -> toProtoService.profiles(items, page))));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> updateBlock(@Nonnull Block request) {
        return uuidOf(request::getId)
                .flatMap(userRepository::findExistingById)
                .invoke(target -> authorizationService.checkNotSelf(target.getId()))
                .map(target -> Tuple2.of(target.getId(), request))
                .call(pair -> pair.getItem2().getBlocked()
                        ? blockRepository.persistIfNotExists(
                                fromProtoService.newBlock(pair.getItem2().getId()))
                        : blockRepository
                                .deleteByIssuerIdAndTargetId(currentUserService.getUserId(), pair.getItem1())
                                .replaceWithVoid())
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> report(@Nonnull Id request) {
        final var callerId = currentUserService.getUserId();
        return uuidOf(request::getId)
                .flatMap(userRepository::findExistingById)
                .flatMap(target -> currentUserService.getUser().map(caller -> Tuple2.of(caller, target)))
                .invoke(this::checkReportable)
                .map(pair -> Tuple3.of(pair.getItem1().getId(), pair.getItem2().getId(), request.getId()))
                .flatMap(triple -> notifyStaffIfNewReport(
                        persistReportIfAbsent(
                                reportRepository.existsByIssuerIdAndTargetUserId(triple.getItem1(), triple.getItem2()),
                                fromProtoService.newUserReport(triple.getItem3())),
                        Uni.createFrom().item(templateDataService.userFrontLink(triple.getItem2()))));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> absolve(@Nonnull Id request) {
        return uuidOf(request::getId)
                .flatMap(userRepository::findExistingById)
                .flatMap(target -> currentUserService.getUser().map(caller -> Tuple2.of(caller, target)))
                .invoke(this::checkBanOrAbsolve)
                .map(Tuple2::getItem2)
                .map(app.fyreplace.data.entities.User::getId)
                .call(reportRepository::deleteByTargetId)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> ban(@Nonnull BanSentence request) {
        return uuidOf(request::getId)
                .flatMap(userRepository::findExistingById)
                .flatMap(target -> currentUserService.getUser().map(caller -> Tuple2.of(caller, target)))
                .invoke(this::checkBanOrAbsolve)
                .map(pair -> Tuple2.of(pair.getItem2().getId(), request.getDays()))
                .call(pair -> validationService.checkDays(pair.getItem2()))
                .flatMap(pair -> userRepository
                        .updateBanByUserId(pair.getItem1(), pair.getItem2())
                        .replaceWith(pair.getItem1()))
                .flatMap(userRepository::findById)
                .call(userBannedMail::send)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> promote(@Nonnull Promotion request) {
        return currentUserService
                .getUser()
                .invoke(authorizationService::checkSuperuser)
                .flatMap(caller -> uuidOf(request::getId)
                        .flatMap(userRepository::findExistingById)
                        .map(target -> Tuple2.of(caller, target)))
                .map(pair -> Tuple2.of(pair, request.getRank()))
                .invoke(pair -> checkPromotion(pair.getItem2(), pair.getItem1()))
                .call(pair -> userRepository.updateRankByUserId(
                        pair.getItem1().getItem2().getId(), fromProtoService.rank(pair.getItem2())))
                .replaceWith(Empty::getDefaultInstance);
    }

    private void checkReportable(
            @Nonnull Tuple2<app.fyreplace.data.entities.User, app.fyreplace.data.entities.User> pair) {
        authorizationService.checkNotSelf(pair.getItem2().getId());
        authorizationService.checkTargetActive(pair.getItem2());
        authorizationService.checkTargetNotBanned(pair.getItem2());
        authorizationService.checkReportable(pair.getItem1(), pair.getItem2());
    }

    private void checkBanOrAbsolve(
            @Nonnull Tuple2<app.fyreplace.data.entities.User, app.fyreplace.data.entities.User> pair) {
        authorizationService.checkNotSelf(pair.getItem2().getId());
        authorizationService.checkRankSufficient(pair.getItem1(), pair.getItem2());
    }

    private void checkPromotion(
            @Nonnull Rank rank,
            @Nonnull Tuple2<app.fyreplace.data.entities.User, app.fyreplace.data.entities.User> pair) {
        authorizationService.checkNotSelf(pair.getItem2().getId());
        authorizationService.checkPromotionRank(rank, pair.getItem2());
    }
}
