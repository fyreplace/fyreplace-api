package app.fyreplace.data.entities;

import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

@Getter
@Setter
@MappedSuperclass
public abstract class TimestampedEntityBase extends EntityBase {
    public static final Sort SORTING = Sort.by("dateCreated", "id");

    @Column(nullable = false)
    @CreationTimestamp(source = SourceType.DB)
    public OffsetDateTime dateCreated;
}
