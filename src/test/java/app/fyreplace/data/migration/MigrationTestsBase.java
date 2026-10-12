package app.fyreplace.data.migration;

import app.fyreplace.data.DataTestsBase;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.List;

public abstract class MigrationTestsBase extends DataTestsBase {
    protected @Nonnull Uni<List<Post>> getPostsFromUser(@Nonnull String username) {
        return userRepository
                .findByUsername(username)
                .map(User::getId)
                .map(postRepository::findAllByAuthorId)
                .flatMap(PanacheQuery::list);
    }
}
