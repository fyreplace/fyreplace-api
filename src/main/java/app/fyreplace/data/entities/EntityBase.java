package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@MappedSuperclass
public abstract class EntityBase {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    public UUID id;

    public interface RepositoryBase<E extends EntityBase> extends PanacheRepositoryBase<E, UUID> {}
}
