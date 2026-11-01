package app.fyreplace.services;

import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.EntityBase;
import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.Report;
import app.fyreplace.data.entities.Subscription;
import app.fyreplace.data.entities.User;
import app.fyreplace.data.entities.Vote;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.Rank;
import com.google.protobuf.ByteString;
import io.quarkus.panache.common.Page;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public final class FromProtoService {
    @Inject
    CurrentUserService currentUserService;

    public @Nonnull UUID uuid(@Nonnull ByteString id) {
        if (id.size() != 16) {
            throw new InvalidArgumentException("invalid_uuid");
        }

        final var buffer = id.asReadOnlyByteBuffer();
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public @Nonnull User.Rank rank(@Nonnull Rank rank) {
        return switch (rank) {
            case Rank.RANK_SUPERUSER -> User.Rank.ADMIN;
            case Rank.RANK_STAFF -> User.Rank.STAFF;
            case Rank.RANK_CITIZEN, Rank.RANK_UNSPECIFIED, Rank.UNRECOGNIZED -> User.Rank.CITIZEN;
        };
    }

    public @Nonnull Chapter.Location location(@Nonnull app.fyreplace.protos.ChapterLocation chapterLocation) {
        return new Chapter.Location(uuid(chapterLocation.getPostId()), chapterLocation.getPosition());
    }

    public @Nonnull Chapter.Location location(@Nonnull app.fyreplace.protos.ChapterRelocation relocation) {
        return new Chapter.Location(uuid(relocation.getPostId()), relocation.getFromPosition());
    }

    public @Nonnull Page page(@Nonnull Header header, @Nonnull app.fyreplace.protos.Cursor cursor) {
        final var data = cursor.getDataList();

        if (data.isEmpty()) {
            return new Page(0, header.getSize());
        }

        if (data.size() != 2
                || !data.get(0).getKey().equals("index")
                || !data.get(1).getKey().equals("size")) {
            throw new InvalidArgumentException("invalid_cursor");
        }

        try {
            return new Page(
                    Integer.parseInt(data.get(0).getValue()),
                    Integer.parseInt(data.get(1).getValue()));
        } catch (NumberFormatException exception) {
            throw new InvalidArgumentException("invalid_cursor");
        }
    }

    public @Nonnull User newUser(@Nonnull app.fyreplace.protos.UserCreation userCreation) {
        final var newUser = new User();
        newUser.setUsername(userCreation.getUsername());
        newUser.setEmail(userCreation.getEmail());
        newUser.setPassword("");
        return newUser;
    }

    public @Nonnull Block newBlock(@Nonnull ByteString targetId) {
        final var newBlock = new Block();
        newBlock.setIssuer(reference(new User(), currentUserService.getUserId()));
        newBlock.setTarget(reference(new User(), uuid(targetId)));
        return newBlock;
    }

    public @Nonnull Post newPost() {
        final var newPost = new Post();
        newPost.setAuthor(reference(new User(), currentUserService.getUserId()));
        return newPost;
    }

    public @Nonnull Chapter newChapter(@Nonnull Chapter.Location location) {
        final var newChapter = new Chapter();
        newChapter.setPost(reference(new Post(), location.postId()));
        newChapter.setPosition(location.position());
        return newChapter;
    }

    public @Nonnull Comment newComment(@Nonnull app.fyreplace.protos.CommentCreation commentCreation) {
        final var newComment = new Comment();
        newComment.setAuthor(reference(new User(), currentUserService.getUserId()));
        newComment.setPost(reference(new Post(), uuid(commentCreation.getPostId())));
        newComment.setText(commentCreation.getText());
        return newComment;
    }

    public @Nonnull Subscription newSubscription(@Nonnull ByteString postId) {
        return newSubscription(uuid(postId));
    }

    public @Nonnull Subscription newSubscription(@Nonnull UUID postId) {
        final var newSubscription = new Subscription();
        newSubscription.setUser(reference(new User(), currentUserService.getUserId()));
        newSubscription.setPost(reference(new Post(), postId));
        return newSubscription;
    }

    public @Nonnull Vote newVote(@Nonnull app.fyreplace.protos.Vote vote) {
        final var newVote = new Vote();
        newVote.setUser(reference(new User(), currentUserService.getUserId()));
        newVote.setPost(reference(new Post(), uuid(vote.getPostId())));
        newVote.setSpread(vote.getSpread());
        return newVote;
    }

    public @Nonnull MessagingToken newMessagingToken(@Nonnull app.fyreplace.protos.MessagingToken messagingToken) {
        final var token = new MessagingToken();
        token.setUser(reference(new User(), currentUserService.getUserId()));
        token.setService(
                switch (messagingToken.getService()) {
                    case MESSAGING_SERVICE_APNS -> MessagingToken.Service.APNS;
                    case MESSAGING_SERVICE_FCM -> MessagingToken.Service.FCM;
                    case MESSAGING_SERVICE_UNSPECIFIED, UNRECOGNIZED -> null;
                });
        token.setToken(messagingToken.getToken());
        return token;
    }

    public @Nonnull Report newUserReport(@Nonnull ByteString targetId) {
        final var newReport = new Report();
        newReport.setIssuer(reference(new User(), currentUserService.getUserId()));
        newReport.setTargetUser(reference(new User(), uuid(targetId)));
        return newReport;
    }

    public @Nonnull Report newPostReport(@Nonnull ByteString targetId) {
        final var newReport = new Report();
        newReport.setIssuer(reference(new User(), currentUserService.getUserId()));
        newReport.setTargetPost(reference(new Post(), uuid(targetId)));
        return newReport;
    }

    public @Nonnull Report newCommentReport(@Nonnull ByteString targetId) {
        final var newReport = new Report();
        newReport.setIssuer(reference(new User(), currentUserService.getUserId()));
        newReport.setTargetComment(reference(new Comment(), uuid(targetId)));
        return newReport;
    }

    private static <E extends EntityBase> @Nonnull E reference(@Nonnull E entity, @Nonnull UUID id) {
        entity.setId(id);
        return entity;
    }
}
