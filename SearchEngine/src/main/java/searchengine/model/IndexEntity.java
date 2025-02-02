package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table( name = "`index`",
        indexes = @jakarta.persistence.Index(
            name = "Idx__index__page_id__lemma",
            columnList = "page_id, lemma_id",
            unique = true)
)
@Getter
@Setter
@NoArgsConstructor
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false, foreignKey = @ForeignKey(name="FK_index_page"))
    private PageEntity pageEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false, foreignKey = @ForeignKey(name="FK_index_lemma"))
    private LemmaEntity lemmaEntity;

    @Column(name = "`rank`", columnDefinition = "float", nullable = false)
    private Integer rank;

    public IndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, Integer rank) {
        this.pageEntity = pageEntity;
        this.lemmaEntity = lemmaEntity;
        this.rank = rank;
    }
}
