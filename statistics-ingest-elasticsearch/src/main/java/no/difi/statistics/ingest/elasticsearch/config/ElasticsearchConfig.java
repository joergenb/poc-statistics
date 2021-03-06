package no.difi.statistics.ingest.elasticsearch.config;

import no.difi.statistics.elasticsearch.Client;
import no.difi.statistics.ingest.IngestService;
import no.difi.statistics.ingest.config.BackendConfig;
import no.difi.statistics.ingest.elasticsearch.ElasticsearchIngestService;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ElasticsearchConfig implements BackendConfig {

    private final Environment environment;

    @Autowired
    public ElasticsearchConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public IngestService ingestService() {
        return new ElasticsearchIngestService(elasticsearchHighLevelClient());
    }

    @Bean
    public Client elasticsearchClient() {
        return new Client(
                elasticsearchHighLevelClient(),
                "http://" + elasticsearchHost() + ":" + elasticsearchPort()
        );
    }

    @Bean(destroyMethod = "close")
    public RestHighLevelClient elasticsearchHighLevelClient() {
        return new RestHighLevelClient(elasticsearchLowLevelClient());
    }

    private RestClientBuilder elasticsearchLowLevelClient() {
        String host = environment.getRequiredProperty("no.difi.statistics.elasticsearch.host");
        int port = environment.getRequiredProperty("no.difi.statistics.elasticsearch.port", Integer.class);
        return RestClient.builder(new HttpHost(host, port, "http"));
    }

    private String elasticsearchHost() {
        return environment.getRequiredProperty("no.difi.statistics.elasticsearch.host");
    }

    private int elasticsearchPort() {
        return environment.getRequiredProperty("no.difi.statistics.elasticsearch.port", Integer.class);
    }

}
