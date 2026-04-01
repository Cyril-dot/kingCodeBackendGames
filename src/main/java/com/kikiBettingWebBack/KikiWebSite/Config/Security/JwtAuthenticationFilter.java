package com.kikiBettingWebBack.KikiWebSite.Config.Security;

import com.kikiBettingWebBack.KikiWebSite.repos.AdminRepository;
import com.kikiBettingWebBack.KikiWebSite.repos.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepo;
    private final AdminRepository adminRepo;

    private static final List<String> PUBLIC_PREFIXES = Arrays.asList(
            "/api/auth/",
            "/api/v1/auth",
            "/api/v1/admin/login",
            "/api/v1/admin/register",
            "/api/v1/users/login",
            "/api/v1/users/register",
            // ── Football endpoints (public) ──────────────────────────────
            "/api/football/",
            "/api/v1/football/",
            // ── Misc public ──────────────────────────────────────────────
            "/api/v1/payment/webhook",
            "/test/",
            "/login",
            "/ping",
            "/actuator/",
            "/ws/",
            "/ws-meeting/",
            "/.well-known/"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/favicon.ico") || path.equals("/ping")) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null
                && existingAuth.isAuthenticated()
                && !(existingAuth instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                // No token — let Spring Security's permitAll() rules decide
                filterChain.doFilter(request, response);
                return;
            }

            String token  = header.substring(7);
            String email  = tokenService.getEmailFromAccessToken(token);
            String role   = tokenService.getRoleFromAccessToken(token);

            log.debug("🔍 Token role: '{}' for email: {}", role, email);

            UserDetails userDetails;

            if ("ADMIN".equals(role) || "SELLER".equals(role)) {
                var adminOpt = adminRepo.findByEmail(email);
                if (adminOpt.isEmpty()) {
                    log.warn("❌ No admin found for email: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                userDetails = new AdminPrincipal(adminOpt.get());
            } else {
                var userOpt = userRepo.findByEmail(email);
                if (userOpt.isEmpty()) {
                    log.warn("❌ No user found for email: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                userDetails = new UserPrincipal(userOpt.get());
            }

            var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("✅ Authenticated: {}", email);

        } catch (Exception e) {
            log.error("💥 JWT error: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
            // Still continue — Spring Security will enforce auth on protected routes
        }

        filterChain.doFilter(request, response);
    }
}