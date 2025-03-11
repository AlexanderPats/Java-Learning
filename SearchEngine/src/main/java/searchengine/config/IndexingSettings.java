package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class IndexingSettings {

    @Value("${exclude-url-parameters:true}")
    private boolean excludeUrlParameters;

    @Value("${path-max-length:767}")
    private int pathMaxLength;
    public void setPathMaxLength(int pathMaxLength) {
        this.pathMaxLength = Math.min(pathMaxLength, 767);
    }

    @Value("${user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0}")
    private String userAgent;

    @Value("${referrer:https://www.yandex.ru/}")
    private String referrer;

    @Value("${request-timeout:500}")
    private int requestTimeout;

    @Value("${check-visited-pages-algorithm:1}")
    private int checkVisitedPagesAlgorithm;

    private Set<Site> sites;
    public void setSites(Set<Site> sites) {
        this.sites = new HashSet<>();
        for (Site site : sites) {
            String url = getResponseSiteUrl(site.getUrl());
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            String name = site.getName();
            this.sites.add(new Site(url, name));
        }
    }

    public String getResponseSiteUrl(String url) {
        Document htmlDoc = null;
        try {
            htmlDoc = Jsoup.connect(url).userAgent(userAgent).referrer(referrer).get();
        } catch (IOException e) {
            log.warn("Check site {} - Site is unavailable: {}", url, e.toString());
        }
        if (!(htmlDoc == null)) {
            url = htmlDoc.connection().response().url().toString();
        }
        return url.toLowerCase();
    }

}
