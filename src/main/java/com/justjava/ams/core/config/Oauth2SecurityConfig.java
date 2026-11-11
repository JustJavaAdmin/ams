package com.justjava.ams.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

@Slf4j
@Configuration
public class Oauth2SecurityConfig {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Bean
    protected SecurityFilterChain configure(HttpSecurity http, ClientRegistrationRepository repo) throws Exception {
        log.debug("Configuring OAuth2 security for AMS");

        http.securityMatcher("/**")
                // REMOVED: .anonymous(disable) - Keep enabled so unauthenticated users can see index
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
                .csrf(CsrfConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(Customizer.withDefaults())
                        .tokenEndpoint(Customizer.withDefaults())
                        .userInfoEndpoint(Customizer.withDefaults())
                        .successHandler(authenticationSuccessHandler())
                )
                .authorizeHttpRequests(authorize -> {

                    authorize.requestMatchers("/", "/index", "/index.html").permitAll();
                    authorize.requestMatchers("/static/**", "/css/**", "/js/**", "/webjars/**").permitAll();
                    authorize.requestMatchers("/images/**", "/fonts/**").permitAll();
                    authorize.requestMatchers("/favicon.ico", "/error").permitAll();
                    authorize.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();

                    authorize.requestMatchers(HttpMethod.GET, "/api/organizations/**").authenticated();
                    authorize.requestMatchers(HttpMethod.GET, "/api/branches/**").authenticated();

                    authorize.requestMatchers("/api/organizations/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isFinanceAdmin()));

                    authorize.requestMatchers("/api/branches/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isFinanceAdmin()));

                    authorize.requestMatchers("/api/financeAdmin/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isFinanceAdmin()));

                    authorize.requestMatchers("/api/posted-journals/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isAccountant()
                                    || authenticationManager.isCfo()));

                    authorize.requestMatchers("/api/system-admin/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isSystemAdmin()));

                    authorize.requestMatchers("/api/accountant/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isAccountant()));

                    authorize.requestMatchers("/api/cfo/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isCfo()));

                    authorize.requestMatchers("/api/auditor/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isAuditor()));

                    authorize.requestMatchers("/api/**").authenticated();

                    authorize.requestMatchers("/admin/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isAdmin()));

                    authorize.requestMatchers("/financeAdmin/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isFinanceAdmin()));

                    authorize.requestMatchers("/system-admin/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isSystemAdmin()));

                    authorize.requestMatchers("/accountant/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isAccountant()));

                    authorize.requestMatchers("/cfo/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isCfo()));

                    authorize.requestMatchers("/auditor/**").access((authentication, context) ->
                            new AuthorizationDecision(authenticationManager.isAuditor()));


                    authorize.anyRequest().authenticated();
                })
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(repo))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutUrl("/users/logout")
                );
        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository repository) {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(repository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return logoutSuccessHandler;
    }

    private AuthenticationSuccessHandler authenticationSuccessHandler(){
        return (request, response, authentication) -> {
            response.sendRedirect("/");
        };
    }
}
