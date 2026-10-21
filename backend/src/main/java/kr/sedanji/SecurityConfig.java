package kr.sedanji;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import kr.sedanji.admin.AdminUserEntity;
import kr.sedanji.admin.AdminUserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
    @Bean UserDetailsService users(@Value("${sedanji.admin.username}") String username,
                                  @Value("${sedanji.admin.password}") String password, AdminUserRepository admins) {
        if (admins.findByUsername(username).isEmpty()) {
            if (password.length() < 16 || password.equals("CHANGE_ME"))
                throw new IllegalStateException("ADMIN_PASSWORD must contain at least 16 characters");
            admins.saveAndFlush(new AdminUserEntity(username, "{bcrypt}" + new BCryptPasswordEncoder().encode(password)));
        }
        return login -> {
            var admin = admins.findByUsername(login).orElseThrow(() -> new UsernameNotFoundException("관리자를 찾을 수 없습니다."));
            return User.withUsername(admin.getUsername()).password(admin.getPasswordHash()).disabled(!admin.isEnabled()).roles("ADMIN").build();
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/restaurants", "/api/csrf", "/api/session", "/api/login", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().denyAll())
            .formLogin(login -> login.loginProcessingUrl("/api/login")
                .successHandler((req, res, auth) -> res.setStatus(204))
                .failureHandler((req, res, ex) -> res.setStatus(401)))
            .logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> res.setStatus(401))
                .accessDeniedHandler((req, res, ex) -> res.setStatus(403)))
            .build();
    }
}
