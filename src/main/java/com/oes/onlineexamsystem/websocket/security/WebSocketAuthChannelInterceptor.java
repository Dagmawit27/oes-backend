package com.oes.onlineexamsystem.websocket.security;

import com.oes.onlineexamsystem.auth.security.CustomUserDetailsService;
import com.oes.onlineexamsystem.auth.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor.class);

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor(JwtTokenProvider tokenProvider,
                                           CustomUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String token = extractToken(accessor);

        if (!StringUtils.hasText(token)) {
            log.warn("STOMP CONNECT rejected: Missing authorization token");
            throw new AccessDeniedException("Authentication token required for WebSocket connection");
        }

        if (!tokenProvider.validateToken(token)) {
            log.warn("STOMP CONNECT rejected: Invalid or expired JWT token");
            throw new AccessDeniedException("Invalid or expired authentication token");
        }

        String username = tokenProvider.getUsernameFromToken(token);
        if (!StringUtils.hasText(username)) {
            log.warn("STOMP CONNECT rejected: Unable to resolve username from token");
            throw new AccessDeniedException("Invalid token claims");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!userDetails.isEnabled()) {
            log.warn("STOMP CONNECT rejected: User account {} is disabled", username);
            throw new AccessDeniedException("User account is disabled");
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        if (accessor.isMutable()) {
            accessor.setUser(authentication);
            log.info("STOMP CONNECT accepted: user={}, authorities={}", username, userDetails.getAuthorities());
            return message;
        } else {
            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
            mutableAccessor.setUser(authentication);
            log.info("STOMP CONNECT accepted (mutable wrapped): user={}, authorities={}", username, userDetails.getAuthorities());
            return org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Authentication auth = (Authentication) accessor.getUser();
        if (auth == null || !auth.isAuthenticated()) {
            log.warn("STOMP SUBSCRIBE rejected: Unauthenticated user");
            throw new AccessDeniedException("Unauthenticated users cannot subscribe to topics");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        String username = auth.getName();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        // 1. Live Monitoring Topic: only EXAM_OFFICER or ADMIN
        if (destination.startsWith("/topic/monitoring/")) {
            boolean isAuthorized = roles.contains("ROLE_EXAM_OFFICER") || roles.contains("ROLE_ADMIN");
            if (!isAuthorized) {
                log.warn("STOMP SUBSCRIBE forbidden: User {} (roles={}) attempted access to {}", username, roles, destination);
                throw new AccessDeniedException("Forbidden: Only Exam Officers and Administrators may access live examination monitoring");
            }
        }

        // 2. Instructor Topics: only INSTRUCTOR or ADMIN
        if (destination.startsWith("/topic/notifications/instructors")) {
            boolean isAuthorized = roles.contains("ROLE_INSTRUCTOR") || roles.contains("ROLE_ADMIN");
            if (!isAuthorized) {
                log.warn("STOMP SUBSCRIBE forbidden: User {} attempted access to instructor topic", username);
                throw new AccessDeniedException("Forbidden: Only instructors may subscribe to this destination");
            }
        }

        // 3. User-specific destinations protection:
        // When subscribing to /user/queue/..., Spring internally routes to the session-specific user queue.
        // Prevent manual forging of other user destinations like /user/otheruser/queue/...
        if (destination.startsWith("/user/") && !destination.startsWith("/user/queue/")) {
            String targetUser = extractTargetUser(destination);
            if (targetUser != null && !targetUser.equalsIgnoreCase(username)) {
                log.warn("STOMP SUBSCRIBE forbidden: User {} tried subscribing to private queue of {}", username, targetUser);
                throw new AccessDeniedException("Forbidden: Cannot subscribe to another user's private destination");
            }
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // Check Authorization native header
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
            return header;
        }

        // Check 'token' native header
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        // Check passcode
        String passcode = accessor.getPasscode();
        if (StringUtils.hasText(passcode)) {
            return passcode;
        }

        return null;
    }

    private String extractTargetUser(String destination) {
        // e.g. /user/john@college.edu/queue/notifications -> john@college.edu
        String[] parts = destination.split("/");
        if (parts.length >= 3 && "user".equals(parts[1])) {
            return parts[2];
        }
        return null;
    }
}
