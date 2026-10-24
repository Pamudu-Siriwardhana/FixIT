package com.serviceportal.config;

import com.serviceportal.model.UserAccount;
import com.serviceportal.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return email -> {
            UserAccount u = users.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
            return User.withUsername(u.getEmail())
                    .password(u.getPassword())
                    .roles(u.getRole().name())
                    .build();
        };
    }

    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (request, response, authentication) -> {
            String target = "/customer/home";
            for (var a : authentication.getAuthorities()) {
                if (a.getAuthority().equals("ROLE_TECHNICIAN")) target = "/technician/home";
                if (a.getAuthority().equals("ROLE_ADMIN")) target = "/admin/home";
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/customer/**", "/api/customer/**", "/api/discovery/**").hasRole("CUSTOMER")
                .requestMatchers("/technician/**", "/api/technician/**").hasRole("TECHNICIAN")
                .anyRequest().authenticated())
            // The REST API uses the same session login; CSRF is switched off for /api/** only.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());
        return http.build();
    }
}
