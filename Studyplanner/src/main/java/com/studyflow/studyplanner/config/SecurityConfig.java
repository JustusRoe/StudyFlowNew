package com.studyflow.studyplanner.config;

import com.studyflow.studyplanner.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Autowired
    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    // Bean password encoder to hash passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationManager-Bean to use autologin after signup
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Main security/filter instance to separete for e.g. public/private pages
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
          .csrf(csrf -> csrf.disable())

          // Whitelist for all public pages (accessable without login)
          .authorizeHttpRequests(auth -> auth
              .requestMatchers("/", "/index.html", "/signup", "/login", 
              "/forgot-password", "/reset-password",
              "/css/**", "/js/**", "/h2-console/**", "/images/**").permitAll()
              .anyRequest().authenticated()
          )

          // Login form config
          .formLogin(form -> form
              .loginPage("/login")
              .loginProcessingUrl("/login")
              .defaultSuccessUrl("/dashboard", false)
              .failureUrl("/login?error=true")
              .permitAll()
          )

          // Logout config
          .logout(logout -> logout
              .logoutUrl("/logout")
              .logoutSuccessUrl("/login?logout=true")
              .deleteCookies("JSESSIONID")
              .invalidateHttpSession(true)
              .permitAll()
          )

          // Allow frameOptions for H2 Database
          .headers(headers -> headers
              .frameOptions(frame -> frame.sameOrigin())
          )

          // UserDetailsService connection to password encoder
          .userDetailsService(userDetailsService)
        ;

        return http.build();
    }
}
