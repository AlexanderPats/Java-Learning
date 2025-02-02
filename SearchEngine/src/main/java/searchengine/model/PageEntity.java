package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table( name = "page",
        indexes = @jakarta.persistence.Index(
            name = "Idx__page__site_id__path",
            columnList = "site_id, path",
            unique = true)
)
@Getter
@Setter
@NoArgsConstructor
public class PageEntity implements Comparable<PageEntity> {

    // 768 - максимальное значение в MYSQL8 с кодировкой utf8mb4 для работы индекса.
    // Т.к. индекс составной (site_id, path), то MAX(path) = 768 - (size of site_id) = 767
    // Данное поле не учитывается при использовании Liquibase
    @Transient
    public static final int MAX_PATH_LENGTH = 767;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name="FK_page_site"))
    private SiteEntity siteEntity;

    @Column(columnDefinition = "varchar(" + MAX_PATH_LENGTH + ")", nullable = false)
    private String path;

    @Column(nullable = false)
    private Integer code;

    @Column(columnDefinition = "mediumtext", nullable = false)
    private String content;

    @OneToMany(mappedBy = "pageEntity", cascade = CascadeType.REMOVE)
    private List<IndexEntity> indexEntities = new ArrayList<>();

    public PageEntity(SiteEntity siteEntity, String path, Integer code, String content) {
        this.siteEntity = siteEntity;
        this.path = path;
        this.code = code;
        this.content = content;
    }

    @Override
    public int compareTo(PageEntity p) {
        String s1 = siteEntity.getUrl().concat(path);
        String s2 = p.getSiteEntity().getUrl().concat( p.getPath() );
        return s1.compareToIgnoreCase(s2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageEntity pageEntity = (PageEntity) o;
        return siteEntity.getUrl().equalsIgnoreCase( pageEntity.siteEntity.getUrl() ) &&
                path.equalsIgnoreCase( pageEntity.path );
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteEntity.getUrl().toLowerCase(), path.toLowerCase());
    }
}
