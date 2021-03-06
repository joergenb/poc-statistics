package no.difi.statistics.ingest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.difi.statistics.ingest.IngestService;
import no.difi.statistics.ingest.config.AppConfig;
import no.difi.statistics.model.TimeSeriesDefinition;
import no.difi.statistics.model.TimeSeriesPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static no.difi.statistics.model.MeasurementDistance.minutes;
import static org.apache.tomcat.util.codec.binary.Base64.encodeBase64;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = {AppConfig.class, MockBackendConfig.class})
@AutoConfigureMockMvc
public class IngestRestControllerTest {

    @Autowired
    private RestTemplate authenticationRestTemplate;
    private MockRestServiceServer authenticationService;

    @Before
    public void setup() {
        authenticationService = MockRestServiceServer.bindTo(authenticationRestTemplate).build();
    }

    @Autowired
    private IngestService service;

    @Autowired
    private MockMvc mockMvc;

    @After
    public void resetMocks() {
        reset(service);
    }

    @Test
    public void whenRequestingIndexThenAuthorizationIsNotRequired() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().is(HttpStatus.FOUND.value())).andExpect(header().string("Location", equalTo("swagger-ui.html")));
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is(HttpStatus.OK.value()));
    }

    @Test
    public void whenIngestingAndUserIsNotTheSameAsOwnerThenAccessIsDenied() throws Exception {
        validCredentials("aUser", "aPassword");
        mockMvc.perform(
                request()
                        .owner("anotherUser")
                        .content(json(aPoint()))
                        .distance("minutes")
                        .ingest()
        )
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    public void whenSendingRequestWithValidTimeSeriesPointAndValidLoginThenExpectValuesSentToServiceMethodToBeTheSameAsSentToService() throws Exception {
        validCredentials("aUser", "aPassword");
        TimeSeriesPoint timeSeriesPoint = aPoint();
        mockMvc.perform(
                request()
                        .content(json(singletonList(timeSeriesPoint)))
                        .distance("minutes")
                        .ingest()
        )
                .andExpect(status().is(HttpStatus.OK.value()));
        verify(service).ingest(
                eq(TimeSeriesDefinition.builder().name("aTimeSeries").distance(minutes).owner("aUser")),
                eq(singletonList(timeSeriesPoint))
        );
    }

    @Test
    public void whenSendingValidMinuteRequestThenExpectNormalResponse() throws Exception {
        validCredentials("aUser", "aPassword");
        mockMvc.perform(
                request()
                        .content(json(singletonList(aPoint())))
                        .distance("minutes")
                        .ingest()
        )
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    @Test
    public void whenSendingRequestWithInvalidContentThenExpect400Response() throws Exception {
        validCredentials("aUser", "aPassword");
        mockMvc.perform(
                request()
                        .content("invalidJson")
                        .distance("minutes")
                        .ingest()
        )
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    public void whenSendingRequestWithWrongPasswordThenExpect401Response() throws Exception {
        invalidCredentials("aUser", "wrongPassword");
        mockMvc.perform(
                request()
                        .password("wrongPassword")
                        .content(json(aPoint()))
                        .distance("minutes")
                        .ingest()
        )
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    public void whenRequestingLastPointInASeriesThenNoAuthenticationIsRequired() throws Exception {
        when(service.last(any(TimeSeriesDefinition.class))).thenReturn(aPoint());
        mockMvc.perform(request().distance("minutes").last())
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    @Test
    public void whenRequestingLastPointInEmptySeriesThenExpectEmptyResponse() throws Exception {
        mockMvc.perform(request().distance("minutes").last())
                .andExpect(status().is(HttpStatus.NO_CONTENT.value()))
                .andExpect(MockMvcResultMatchers.content().string(""));
    }

    @Test
    public void whenSendingValidHourRequestThenExpectNormalResponse() throws Exception {
        validCredentials("aUser", "aPassword");
        mockMvc.perform(request().content(json(singletonList(aPoint()))).distance("hours").ingest())
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    @Test
    public void whenBulkIngestingTwoPointThenExpectOkResponse() throws Exception {
        validCredentials("aUser", "aPassword");
        mockMvc.perform(request().content(json(asList(aPoint(), aPoint()))).distance("hours").ingest())
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    private TimeSeriesPoint aPoint() {
        return TimeSeriesPoint.builder()
                .measurement("antall", 2)
                .timestamp(ZonedDateTime.of(2016, 3, 3, 20, 12, 13, 12, ZoneId.of("UTC")))
                .build();
    }

    public static RequestBuilder request() {
        return new RequestBuilder();
    }

    public static class RequestBuilder {
        private String owner = "aUser";
        private String series = "aTimeSeries";
        private String user = "aUser";
        private String password = "aPassword";
        private String content;
        private String distance;

        RequestBuilder owner(String owner) {
            this.owner = owner;
            return this;
        }

        RequestBuilder series(String series) {
            this.series = series;
            return this;
        }

        RequestBuilder user(String user) {
            this.user = user;
            return this;
        }

        RequestBuilder password(String password) {
            this.password = password;
            return this;
        }

        RequestBuilder content(String content) {
            this.content = content;
            return this;
        }

        RequestBuilder distance(String distance) {
            this.distance = distance;
            return this;
        }

        private String authorizationHeader(String username, String password) {
            return "Basic " + new String(encodeBase64((username + ":" + password).getBytes()));
        }

        MockHttpServletRequestBuilder ingest() {
            return post("/{owner}/{seriesName}/{distance}", owner, series, distance)
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .header("Authorization", authorizationHeader(user, password))
                    .content(content);
        }

        MockHttpServletRequestBuilder last() {
            return get("/{owner}/{seriesName}/{distance}/last", owner, series, distance);
        }

    }

    private String json(Object object) throws Exception {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .writeValueAsString(object);
    }

    private void validCredentials(String username, String password) {
        authenticationService
                .expect(once(), requestTo("http://authenticate:8080/authentications"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("username", equalTo(username)))
                .andExpect(jsonPath("password", equalTo(password)))
                .andRespond(withSuccess("{\"authenticated\": true}", MediaType.APPLICATION_JSON_UTF8));
    }

    private void invalidCredentials(String username, String password) {
        authenticationService
                .expect(once(), requestTo("http://authenticate:8080/authentications"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(jsonPath("username", equalTo(username)))
                .andExpect(jsonPath("password", equalTo(password)))
                .andRespond(withSuccess("{\"authenticated\": false}", MediaType.APPLICATION_JSON_UTF8));
    }

}
