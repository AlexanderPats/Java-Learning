package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table( name = "lemma",
        indexes = @jakarta.persistence.Index(
            name = "Idx__lemma__site_id__lemma",
            columnList = "site_id, lemma",
            unique = true)
)
@Getter
@Setter
@NoArgsConstructor
public class LemmaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name="FK_lemma_site"))
    private SiteEntity siteEntity;

    @Column(columnDefinition = "varchar(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private Integer frequency;

    @OneToMany(mappedBy = "lemmaEntity", cascade = CascadeType.REMOVE)
    private List<IndexEntity> indexEntities = new ArrayList<>();

    @Override
    public String toString() {
        return siteEntity.getUrl().concat(": ").concat(lemma);
    }

}

