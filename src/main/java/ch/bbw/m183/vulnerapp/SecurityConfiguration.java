package ch.bbw.m183.vulnerapp;

import ch.bbw.m183.vulnerapp.datamodel.UserEntity;
import ch.bbw.m183.vulnerapp.repository.UserRepository;
import ch.bbw.m183.vulnerapp.service.RestfulFormService;
import ch.bbw.m183.vulnerapp.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Configuration
public class SecurityConfiguration {

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            UserEntity user = userRepository.findById(username).orElseThrow();

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            if (user.getUsername().equals("admin")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    authorities
            );
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
                        // GET / - everyone
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        // GET /api/blog - everyone
                        .requestMatchers(HttpMethod.GET, "/api/blog").permitAll()
                        // POST /api/blog - authenticated with CSRF (ROLE_USER)
                        .requestMatchers(HttpMethod.POST, "/api/blog").hasRole("USER")
                        // GET /api/user/whoami - authenticated
                        .requestMatchers(HttpMethod.GET, "/api/user/whoami").authenticated()
                        // /api/admin/* - only admin
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // GET /actuator/health - everyone
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // everything else under /api requires authentication
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .build();
    }
}
