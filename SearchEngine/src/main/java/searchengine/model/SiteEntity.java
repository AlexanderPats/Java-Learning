package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "site")
@Getter
@Setter
@NoArgsConstructor
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SiteStatus status;

    @CreationTimestamp
    @Column(name = "status_time", columnDefinition = "datetime", nullable = false)
    private Instant statusTime;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(columnDefinition = "varchar(255)", nullable = false, unique = true)
    private String url;

    @Column(columnDefinition = "varchar(255)", nullable = false)
    private String name;

//    @Cascade(org.hibernate.annotations.CascadeType.REMOVE)
//    @OneToMany(mappedBy = "siteEntity", cascade = CascadeType.REMOVE)
    @OneToMany(mappedBy = "siteEntity")
    private Set<PageEntity> pageEntities = new HashSet<>();

//    @OneToMany(mappedBy = "siteEntity", cascade = CascadeType.REMOVE)
    @OneToMany(mappedBy = "siteEntity")
    private Set<LemmaEntity> lemmaEntities = new HashSet<>();

    public SiteEntity(SiteStatus status, String lastError, String url, String name) {
        this.status = status;
        this.lastError = lastError;
        this.url = url;
        this.name = name;
    }

    @Override
    public String toString() {
        return "SiteEntity: " + System.lineSeparator() +
                "id=" + id + System.lineSeparator() +
                "status=" + status + System.lineSeparator() +
                "statusTime=" + statusTime + System.lineSeparator() +
                "lastError='" + lastError + '\'' + System.lineSeparator() +
                "url='" + url + '\'' + System.lineSeparator() +
                "name='" + name + '\'';
    }
}
