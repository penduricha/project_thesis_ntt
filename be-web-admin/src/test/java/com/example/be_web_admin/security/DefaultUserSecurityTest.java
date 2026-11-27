package com.example.be_web_admin.security;

import com.example.be_web_admin.BeWebAdminApplication;
import com.example.be_web_admin.chat.security.ChatJwtFilter;
import com.example.be_web_admin.enums.AccountStatus;
import com.example.be_web_admin.enums.Role;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.security.jwt.JwtAuthenticationFilter;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.AdminTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultUserSecurityTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyTheUnusedDefaultUserAndDataRestAreExcluded() {
        SpringBootApplication configuration = BeWebAdminApplication.class.getAnnotation(SpringBootApplication.class);
        assertNotNull(configuration);
        assertArrayEquals(new Class<?>[]{RepositoryRestMvcAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class}, configuration.exclude());
    }

    @Test
    void webBearerFilterStillAuthenticatesAnAllowedAccount() throws Exception {
        AdminJwtTokenProvider provider = mock(AdminJwtTokenProvider.class);
        AdminTokenService tokens = mock(AdminTokenService.class);
        AdminRepository admins = mock(AdminRepository.class);
        Admin admin = new Admin();
        admin.setRole(Role.LECTURER);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        when(provider.parseIdentity("valid", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "00000000-0000-0000-0000-000000000007"));
        when(tokens.findAdminAccessTokenByJwtToken("valid")).thenReturn(true);
        when(admins.findAdminByAdminId(7L)).thenReturn(admin);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/web/admin/questions");
        request.addHeader("Authorization", "Bearer valid");
        new JwtAuthenticationFilter(provider, tokens, admins).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(7L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("ROLE_LECTURER", SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void webBearerFilterRejectsDisabledAccount() throws Exception {
        AdminJwtTokenProvider provider = mock(AdminJwtTokenProvider.class);
        AdminTokenService tokens = mock(AdminTokenService.class);
        AdminRepository admins = mock(AdminRepository.class);
        Admin admin = new Admin();
        admin.setRole(Role.ADMIN);
        admin.setAccountStatus(AccountStatus.DISABLED);
        when(provider.parseIdentity("valid", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "00000000-0000-0000-0000-000000000007"));
        when(tokens.findAdminAccessTokenByJwtToken("valid")).thenReturn(true);
        when(admins.findAdminByAdminId(7L)).thenReturn(admin);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/web/admin/questions");
        request.addHeader("Authorization", "Bearer valid");
        new JwtAuthenticationFilter(provider, tokens, admins).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void mobileTokenCannotAuthenticateWebEndpoint() throws Exception {
        AdminJwtTokenProvider provider = mock(AdminJwtTokenProvider.class);
        AdminTokenService tokens = mock(AdminTokenService.class);
        AdminRepository admins = mock(AdminRepository.class);
        when(provider.parseIdentity("mobile", "ACCESS")).thenReturn(new AuthSessionStore.Identity(
                7L, AuthSessionStore.Client.MOBILE, "00000000-0000-0000-0000-000000000007"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/web/admin/questions");
        request.addHeader("Authorization", "Bearer mobile");
        new JwtAuthenticationFilter(provider, tokens, admins).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tokens);
    }

    @Test
    void chatBearerFilterStillAuthenticatesButBasicCredentialsDoNot() throws Exception {
        AdminJwtTokenProvider provider = mock(AdminJwtTokenProvider.class);
        AdminTokenService tokens = mock(AdminTokenService.class);
        ChatJwtFilter filter = new ChatJwtFilter(provider, tokens);
        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        filter.doFilter(basic, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        when(provider.parseIdentity("valid", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, "00000000-0000-0000-0000-000000000007"));
        when(tokens.findAdminAccessTokenByJwtToken("valid")).thenReturn(true);
        MockHttpServletRequest bearer = new MockHttpServletRequest();
        bearer.addHeader("Authorization", "Bearer valid");
        filter.doFilter(bearer, new MockHttpServletResponse(), new MockFilterChain());
        assertEquals(7L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}
