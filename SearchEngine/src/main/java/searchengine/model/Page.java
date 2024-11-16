package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table( name = "page", indexes = @jakarta.persistence.Index(
        name = "Idx__page__site_id__path",
        columnList = "site_id, path",
        unique = true)
)
@Getter
@Setter
@NoArgsConstructor
public class Page implements Comparable<Page> {

    // 767 - максимальное значение в MYSQL8 с кодировкой utf8mb4 для работы составного индекса (site_id, path)
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

    @OneToMany(mappedBy = "page", cascade = CascadeType.REMOVE)
    private List<Index> indexes = new ArrayList<>();

    public Page(SiteEntity siteEntity, String path, Integer code, String content) {
        this.siteEntity = siteEntity;
        this.path = path;
        this.code = code;
        this.content = content;
    }

    @Override
    public int compareTo(Page p) {
        String s1 = siteEntity.getUrl() + path;
        String s2 = p.getSiteEntity().getUrl() + p.getPath();
        return s1.compareToIgnoreCase(s2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Page page = (Page) o;
        return siteEntity.getUrl().equalsIgnoreCase( page.siteEntity.getUrl() ) &&
                path.equalsIgnoreCase( page.path );
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteEntity.getUrl().toLowerCase(), path.toLowerCase());
    }
}
