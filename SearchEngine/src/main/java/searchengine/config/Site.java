package searchengine.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Setter
@Getter
public class Site {

    private String url;
    private String name;

    public void setUrl(String url) {
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Site site = (Site) o;
        return url.equalsIgnoreCase(site.url);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(url);
    }

    @Override
    public String toString() {
        return "Site{" + "url='" + url + '\'' + ", name='" + name + '\'' + '}';
    }
}
