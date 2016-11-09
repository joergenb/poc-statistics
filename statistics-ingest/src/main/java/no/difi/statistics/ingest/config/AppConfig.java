package no.difi.statistics.ingest.config;

import no.difi.statistics.ingest.api.IngestRestController;
import no.difi.statistics.ingest.poc.DifiAdminIngester;
import no.difi.statistics.ingest.poc.RandomIngesterRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import static java.lang.String.format;
import static springfox.documentation.builders.PathSelectors.any;
import static springfox.documentation.builders.RequestHandlerSelectors.basePackage;

@Configuration
@EnableAutoConfiguration
@EnableWebSecurity
@EnableSwagger2
public class AppConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private BackendConfig backendConfig;

    @Bean
    public IngestRestController api() {
        return new IngestRestController(backendConfig.ingestService());
    }

    @Bean
    public RandomIngesterRestController randomApi() {
        return new RandomIngesterRestController(backendConfig.ingestService());
    }

    @Bean
    public DifiAdminIngester difiAdminIngester() {
        return new DifiAdminIngester(backendConfig.ingestService());
    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication().withUser("984661185").password("123456").roles("USER");
        auth.inMemoryAuthentication().withUser("991825827").password("654321").roles("USER");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                // No authentication required for documentation paths used by Swagger
                .antMatchers("/", "/swagger-ui.html", "/swagger-resources/**", "/v2/api-docs/**").permitAll()
                // Authentication required for ingest methods. Username must be equal to owner of series.
                .antMatchers("/{owner}/{seriesName}/**").access("#owner == authentication.name")
                .anyRequest().authenticated()
                .and()
                .httpBasic()
                .and()
                .csrf().disable();
    }

    @Bean
    public Docket apiDocumentation() {
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("statistikk-inndata")
                .select()
                .apis(basePackage(IngestRestController.class.getPackage().getName()))
                .paths(any())
                .build()
                .apiInfo(new ApiInfoBuilder()
                        .title("Statistikk for offentlige tjenester")
                        .description(
                                format(
                                        "Beskrivelse av API for innlegging av data (versjon %s).",
                                        System.getProperty("difi.version", "N/A")
                                )
                        )
                        .build()
                );
    }

}
