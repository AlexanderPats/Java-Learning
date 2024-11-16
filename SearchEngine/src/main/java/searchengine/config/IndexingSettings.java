package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

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

    @Value("${request-timeout:1000}")
    private int requestTimeout;

    private Set<Site> sites;

}
