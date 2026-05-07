package ch.bbw.m183.vulnerapp;

import ch.bbw.m183.vulnerapp.repository.UserRepository;
import ch.bbw.m183.vulnerapp.service.RestfulFormService;
import ch.bbw.m183.vulnerapp.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;


@Configuration
public class SecurityConfiguration {

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository){
        return username -> {
            return userRepository.findById(username)
                    .map(x -> new User(x.getUsername(), x.getPassword(), List.of()))
                    .orElseThrow();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RestfulFormService restfulFormService) throws Exception {
        return http
                .csrf((csrf) -> csrf.spa())
                .formLogin(restfulFormService.restfulFormLogin())
                .exceptionHandling(restfulFormService.unauthorizedPerDefault())
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.GET, "/api/blog")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .build();
    }
}
