package ch.bbw.m183.vulnerapp.service;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Default Spring Form Login shows a dedicated Login page:
 * https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html
 * For our RestFull endpoint we disable the redirect and enforce a direct JSON response:
 * https://stackoverflow.com/questions/71680341/spring-boot-rest-api-disable-form-login-redirect
 */
@Service
@RequiredArgsConstructor
public class RestfulFormService {

    private final UserService userService;

    private final ObjectMapper objectMapper;

    /**
     * Put it in your SecurityFilterChain like {@code .formLogin(restfulFormService.restfulFormLogin())}.
     */
    public Customizer<FormLoginConfigurer<HttpSecurity>> restfulFormLogin() {
        return form -> form.failureHandler((req, res, ex) -> res.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage()))
                .successHandler((request, response, auth) -> {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("application/json");
                    response.getWriter().write(objectMapper.writeValueAsString(userService.whoami(auth.getName(), )));
                });
    }

    /**
     * Put it in your SecurityFilterChain like {@code .exceptionHandling(restfulFormService.unauthorizedPerDefault())}.
     */
    public Customizer<ExceptionHandlingConfigurer<HttpSecurity>> unauthorizedPerDefault() {
        return ex -> ex.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                request -> request.getRequestURI().startsWith("/api/"));
    }

}