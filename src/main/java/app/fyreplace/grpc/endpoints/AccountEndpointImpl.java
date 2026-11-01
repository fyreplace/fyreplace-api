package app.fyreplace.grpc.endpoints;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.AccountActivationMail;
import app.fyreplace.mails.AccountConnectionMail;
import app.fyreplace.protos.AccountService;
import app.fyreplace.protos.ConnectionCredentials;
import app.fyreplace.protos.ConnectionToken;
import app.fyreplace.protos.Connections;
import app.fyreplace.protos.Email;
import app.fyreplace.protos.Id;
import app.fyreplace.protos.Token;
import app.fyreplace.protos.UserCreation;
import app.fyreplace.services.AuthorizationService;
import app.fyreplace.services.JwtService;
import app.fyreplace.services.ValidationService;
import app.fyreplace.services.gravatar.GravatarService;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

@GrpcService
public final class AccountEndpointImpl extends EndpointBase implements AccountService {
    @Inject
    AccountActivationMail accountActivationMail;

    @Inject
    AccountConnectionMail accountConnectionMail;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    JwtService jwtService;

    @Inject
    ValidationService validationService;

    @Inject
    GravatarService gravatarService;

    @Override
    @WithTransaction
    public @Nonnull Uni<Empty> create(@Nonnull UserCreation request) {
        return Uni.createFrom()
                .item(request::getUsername)
                .call(validationService::checkUsernameReserved)
                .call(validationService::checkUsernameAvailable)
                .call(validationService::checkUsername)
                .replaceWith(request::getEmail)
                .call(validationService::checkEmailAvailable)
                .call(validationService::checkEmail)
                .replaceWith(request)
                .map(fromProtoService::newUser)
                .flatMap(userRepository::persist)
                .invoke(user -> gravatarService.fetchDefaultAvatarDetached(user.getId()))
                .call(userRepository::deleteExpiredPending)
                .flatMap(accountActivationMail::send)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> delete(@Nonnull Empty request) {
        return Uni.createFrom()
                .item(currentUserService::getUserId)
                .call(postRepository::softDeleteByAuthorId)
                .call(chapterRepository::deleteByPostAuthorId)
                .call(commentRepository::deleteByPostAuthorId)
                .call(commentRepository::softDeleteByAuthorId)
                .call(messagingTokenRepository::deleteByUserId)
                .flatMap(userRepository::softDeleteById)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @WithSession
    public @Nonnull Uni<Empty> sendActivationEmail(@Nonnull Email request) {
        return Uni.createFrom()
                .item(request::getEmail)
                .flatMap(userRepository::findByEmail)
                .call(user -> user.isPending()
                        ? accountActivationMail.send(user)
                        : Uni.createFrom().voidItem())
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @WithTransaction
    public @Nonnull Uni<Token> confirmActivation(@Nonnull ConnectionToken request) {
        return Uni.createFrom()
                .item(request::getToken)
                .flatMap(authorizationService::emailTokenUser)
                .invoke(authorizationService::checkPending)
                .call(user -> userRepository.updateActiveById(user.getId(), true))
                .map(this::userToken);
    }

    @Override
    @Authenticated
    public @Nonnull Uni<Connections> listConnections(@Nonnull Empty request) {
        return Uni.createFrom().item(Connections::getDefaultInstance);
    }

    @Override
    @WithSession
    public @Nonnull Uni<Empty> sendConnectionEmail(@Nonnull Email request) {
        return Uni.createFrom()
                .item(request::getEmail)
                .call(validationService::checkEmail)
                .flatMap(userRepository::findByEmail)
                .invoke(authorizationService::checkUser)
                .invoke(authorizationService::checkPasswordless)
                .call(accountConnectionMail::send)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @WithTransaction
    public @Nonnull Uni<Token> confirmConnection(@Nonnull ConnectionToken request) {
        return Uni.createFrom()
                .item(request::getToken)
                .flatMap(authorizationService::emailTokenUser)
                .invoke(authorizationService::checkUser)
                .map(this::userToken);
    }

    @Override
    public @Nonnull Uni<Token> connect(@Nonnull ConnectionCredentials request) {
        return Uni.createFrom()
                .item(request::getEmail)
                .flatMap(authorizationService::connectUser)
                .invoke(authorizationService::checkUser)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .call(user -> validationService.checkPassword(user, request.getPassword()))
                .emitOn(Infrastructure.getDefaultExecutor())
                .map(this::userToken);
    }

    @Override
    @Authenticated
    public @Nonnull Uni<Empty> disconnect(@Nonnull Id request) {
        return Uni.createFrom().item(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    public @Nonnull Uni<Empty> disconnectAll(@Nonnull Empty request) {
        return Uni.createFrom().item(Empty::getDefaultInstance);
    }

    private @Nonnull Token userToken(@Nonnull User user) {
        return toProtoService.token(jwtService.makeJwt(user, false));
    }
}
