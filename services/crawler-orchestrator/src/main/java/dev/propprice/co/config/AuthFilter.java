package dev.propprice.co.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {
  private final CoAuthProperties props;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.startsWith("/actuator"))
      return true;
    if ("OPTIONS".equalsIgnoreCase(request.getMethod()))
      return true;
    return !props.isEnabled(); // skip when auth disabled
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String auth = req.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer "))
      auth = auth.substring(7);
    if (props.getToken() != null && props.getToken().equals(auth)) {
      chain.doFilter(req, res);
    } else {
      res.setStatus(HttpStatus.UNAUTHORIZED.value());
      res.getWriter().write("Unauthorized");
    }
  }
}
