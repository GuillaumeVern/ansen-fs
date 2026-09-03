package com.losvernos.anzenfs.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import java.util.Arrays;

import com.losvernos.anzenfs.rbac.auth.JwtAuthenticationFilter;
import com.losvernos.anzenfs.rbac.user.UserService;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Autowired
  private UserService userService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  /**
   * StrictHttpFirewall's default allowed HTTP methods (GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS)
   * reject PROPFIND and MKCOL outright (400, before any filter or controller runs) - the WebDAV
   * subset needs both added explicitly.
   */
  @Bean
  public HttpFirewall httpFirewall() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowedHttpMethods(Arrays.asList(
        "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "PROPFIND", "MKCOL"));
    return firewall;
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  /**
   * Separate chain for /webdav/**, evaluated first (lower @Order). WebDAV sync clients (FolderSync,
   * Autosync) only speak Basic Auth, never JWT, so this reuses the same
   * {@link DaoAuthenticationProvider}/{@link PasswordEncoder} as the login form but through Spring
   * Security's own httpBasic() support instead of {@link JwtAuthenticationFilter} - kept as its own
   * chain rather than layered onto the default one so JWT-only /api/** callers are unaffected.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain webDavFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/webdav/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity http) {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/login").permitAll()
            .requestMatchers("/api/users/create").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/**").authenticated()
            .requestMatchers("/**").permitAll());
    return http.build();
  }

}
