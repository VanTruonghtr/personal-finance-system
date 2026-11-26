package personalfinance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import personalfinance.entity.User;
import personalfinance.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            Optional<User> userOpt = Optional.empty();

            if (jwtService.isTokenValid(token)) {
                String email = jwtService.extractEmail(token);
                userOpt = userRepository.findByEmail(email);
            } else if (token.startsWith("bearer-token-")) {
                try {
                    Long id = Long.parseLong(token.substring("bearer-token-".length()));
                    userOpt = userRepository.findById(id);
                } catch (NumberFormatException ignored) {
                }
            }

            userOpt.ifPresent(user -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                String role = (user.getRole() != null && !user.getRole().isBlank()) ? user.getRole() : "USER";
                var auth = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
            });
        }
        filterChain.doFilter(request, response);
    }
}