package com.oes.onlineexamsystem.auth.service;

import com.oes.onlineexamsystem.auth.dto.ChangePasswordRequest;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.auth.dto.LoginResponse;
import com.oes.onlineexamsystem.auth.dto.UserProfileResponse;
import com.oes.onlineexamsystem.auth.entity.RefreshToken;
import com.oes.onlineexamsystem.auth.repository.RefreshTokenRepository;
import com.oes.onlineexamsystem.auth.security.JwtTokenProvider;
import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtTokenProvider tokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().trim(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        String accessToken = tokenProvider.generateAccessToken(userPrincipal);
        String refreshTokenValue = tokenProvider.generateRefreshToken();

        // Revoke any existing active refresh tokens for single-active-token security
        refreshTokenRepository.revokeAllTokensByUserId(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiresAt(Instant.now().plusMillis(tokenProvider.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        // Add secure HttpOnly cookies if HttpServletResponse is present
        if (response != null) {
            setTokenCookie(response, "access_token", accessToken, (int) (tokenProvider.getAccessTokenExpirationMs() / 1000));
            setTokenCookie(response, "refresh_token", refreshTokenValue, (int) (tokenProvider.getRefreshTokenExpirationMs() / 1000));
        }

        Set<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getAccessTokenExpirationMs())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .build();
    }

    @Transactional
    public LoginResponse refreshToken(String refreshTokenValue, HttpServletRequest request, HttpServletResponse response) {
        String tokenToVerify = refreshTokenValue;

        if (!StringUtils.hasText(tokenToVerify) && request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refresh_token".equals(cookie.getName())) {
                    tokenToVerify = cookie.getValue();
                    break;
                }
            }
        }

        if (!StringUtils.hasText(tokenToVerify)) {
            throw new UnauthorizedException("Refresh token is required");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenToVerify)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        if (!refreshToken.isValid()) {
            throw new UnauthorizedException("Refresh token is expired or revoked. Please log in again.");
        }

        User user = refreshToken.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("User account is inactive");
        }

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateAccessToken(userPrincipal);

        if (response != null) {
            setTokenCookie(response, "access_token", newAccessToken, (int) (tokenProvider.getAccessTokenExpirationMs() / 1000));
        }

        Set<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(tokenToVerify)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getAccessTokenExpirationMs())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .build();
    }

    @Transactional
    public void logout(String refreshTokenValue, HttpServletRequest request, HttpServletResponse response) {
        String tokenToRevoke = refreshTokenValue;

        if (!StringUtils.hasText(tokenToRevoke) && request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refresh_token".equals(cookie.getName())) {
                    tokenToRevoke = cookie.getValue();
                    break;
                }
            }
        }

        if (StringUtils.hasText(tokenToRevoke)) {
            refreshTokenRepository.findByToken(tokenToRevoke).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        }

        if (response != null) {
            clearCookie(response, "access_token");
            clearCookie(response, "refresh_token");
        }

        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet());

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("User is not authenticated");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password does not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all active tokens upon password change
        refreshTokenRepository.revokeAllTokensByUserId(user.getId());
    }

    private void setTokenCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        // Note: SameSite is typically configured on ResponseCookie or container
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
