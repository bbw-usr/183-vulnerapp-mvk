package ch.bbw.m183.vulnerapp;

import ch.bbw.m183.vulnerapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AccessControlWebTestClientTest {

	private static final String USER_USERNAME = "fuu";
	private static final String USER_PASSWORD = "bar";
	private static final String ADMIN_USERNAME = "admin";
	private static final String ADMIN_PASSWORD = "super5ecret";
	private static final String BLOG_BODY = """
			{"title":"Test Blog","body":"Test body content for blog post"}
			""";

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private UserRepository userRepository;

	private WebTestClient freshClient() {
		Map<String, String> cookies = new HashMap<>();

		ExchangeFilterFunction cookieFilter = (request, next) -> {
			ClientRequest.Builder builder = ClientRequest.from(request);
			if (!cookies.isEmpty()) {
				String cookieHeader = cookies.entrySet().stream()
						.map(entry -> entry.getKey() + "=" + entry.getValue())
						.collect(Collectors.joining("; "));
				builder.header(HttpHeaders.COOKIE, cookieHeader);
			}
			return next.exchange(builder.build()).doOnNext(response -> response.cookies().forEach((name, cookieList) -> {
				if (!cookieList.isEmpty()) {
					cookies.put(name, cookieList.get(0).getValue());
				}
			}));
		};

		return webTestClient.mutate()
				.filter(cookieFilter)
				.build();
	}

	private String fetchCsrfToken(WebTestClient client) {
		List<ResponseCookie> cookies = client.get()
				.uri("/")
				.exchange()
				.expectStatus().isOk()
				.expectCookie().exists("XSRF-TOKEN")
				.returnResult(Void.class)
				.getResponseCookies()
				.get("XSRF-TOKEN");

		return cookies.getFirst().getValue();
	}

	private void login(WebTestClient client, String username, String password) {
		String csrfToken = fetchCsrfToken(client);
		client.post()
				.uri("/login")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.cookie("XSRF-TOKEN", csrfToken)
				.header("X-XSRF-TOKEN", csrfToken)
				.bodyValue("username=" + username + "&password=" + password)
				.exchange()
				.expectStatus().isOk();
	}

	private WebTestClient.RequestHeadersSpec<?> withCsrf(WebTestClient client, WebTestClient.RequestHeadersSpec<?> spec) {
		String csrfToken = fetchCsrfToken(client);
		return spec.cookie("XSRF-TOKEN", csrfToken)
				.header("X-XSRF-TOKEN", csrfToken);
	}

	private void expectBlocked(WebTestClient.ResponseSpec responseSpec) {
		responseSpec.expectStatus().value(status -> {
			if (status != 401 && status != 403) {
				throw new AssertionError("Expected 401 or 403 but got " + status);
			}
		});
	}

	private String basicAuthForWhoami(String username) {
		var user = userRepository.findById(username).orElseThrow();
		String credentials = username + ":" + user.getPassword();
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

	// GET /

	@Test
	void getRoot_anonymous_isAllowed() {
		freshClient().get()
				.uri("/")
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getRoot_userWithoutCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/")
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getRoot_userWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/")
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getRoot_adminWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, ADMIN_USERNAME, ADMIN_PASSWORD);
		client.get()
				.uri("/")
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	// GET /api/blog

	@Test
	void getBlog_anonymous_isAllowed() {
		freshClient().get()
				.uri("/api/blog")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getBlog_userWithoutCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/api/blog")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getBlog_userWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/api/blog")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getBlog_adminWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, ADMIN_USERNAME, ADMIN_PASSWORD);
		client.get()
				.uri("/api/blog")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	// POST /api/blog

	@Test
	void postBlog_anonymous_isBlocked() {
		expectBlocked(freshClient().post()
				.uri("/api/blog")
				.contentType(APPLICATION_JSON)
				.bodyValue(BLOG_BODY)
				.exchange());
	}

	@Test
	void postBlog_userWithoutCsrf_isBlocked() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.post()
				.uri("/api/blog")
				.contentType(APPLICATION_JSON)
				.bodyValue(BLOG_BODY)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void postBlog_userWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		String csrfToken = fetchCsrfToken(client);
		client.post()
				.uri("/api/blog")
				.contentType(APPLICATION_JSON)
				.cookie("XSRF-TOKEN", csrfToken)
				.header("X-XSRF-TOKEN", csrfToken)
				.bodyValue(BLOG_BODY)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void postBlog_adminWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, ADMIN_USERNAME, ADMIN_PASSWORD);
		String csrfToken = fetchCsrfToken(client);
		client.post()
				.uri("/api/blog")
				.contentType(APPLICATION_JSON)
				.cookie("XSRF-TOKEN", csrfToken)
				.header("X-XSRF-TOKEN", csrfToken)
				.bodyValue(BLOG_BODY)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	// GET /api/user/whoami

	@Test
	void getWhoami_anonymous_isBlocked() {
		freshClient().get()
				.uri("/api/user/whoami")
				.header(HttpHeaders.AUTHORIZATION, basicAuthForWhoami(USER_USERNAME))
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void getWhoami_userWithoutCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/api/user/whoami")
				.header(HttpHeaders.AUTHORIZATION, basicAuthForWhoami(USER_USERNAME))
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getWhoami_userWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/api/user/whoami")
				.header(HttpHeaders.AUTHORIZATION, basicAuthForWhoami(USER_USERNAME))
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getWhoami_adminWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, ADMIN_USERNAME, ADMIN_PASSWORD);
		client.get()
				.uri("/api/user/whoami")
				.header(HttpHeaders.AUTHORIZATION, basicAuthForWhoami(ADMIN_USERNAME))
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	// GET /api/admin/users

	@Test
	void getAdminUsers_anonymous_isBlocked() {
		freshClient().get()
				.uri("/api/admin/users")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void getAdminUsers_userWithoutCsrf_isBlocked() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/api/admin/users")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void getAdminUsers_userWithCsrf_isBlocked() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		withCsrf(client, client.get().uri("/api/admin/users").accept(APPLICATION_JSON))
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void getAdminUsers_adminWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, ADMIN_USERNAME, ADMIN_PASSWORD);
		client.get()
				.uri("/api/admin/users")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	// GET /actuator/health

	@Test
	void getActuatorHealth_anonymous_isAllowedWithoutDetails() {
		freshClient().get()
				.uri("/actuator/health")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody()
				.jsonPath("$.status").exists()
				.jsonPath("$.components").doesNotExist();
	}

	@Test
	void getActuatorHealth_userWithoutCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/actuator/health")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getActuatorHealth_userWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, USER_USERNAME, USER_PASSWORD);
		client.get()
				.uri("/actuator/health")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}

	@Test
	void getActuatorHealth_adminWithCsrf_isAllowed() {
		WebTestClient client = freshClient();
		login(client, ADMIN_USERNAME, ADMIN_PASSWORD);
		client.get()
				.uri("/actuator/health")
				.accept(APPLICATION_JSON)
				.exchange()
				.expectStatus().is2xxSuccessful();
	}
}
