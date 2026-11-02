package com.oes.onlineexamsystem.user.service;

import com.oes.onlineexamsystem.auth.repository.RefreshTokenRepository;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.user.dto.CreateUserRequest;
import com.oes.onlineexamsystem.user.dto.UpdateUserRequest;
import com.oes.onlineexamsystem.user.dto.UserResponse;
import com.oes.onlineexamsystem.user.entity.Role;
import com.oes.onlineexamsystem.user.entity.RoleName;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.RoleRepository;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getAllUsers(String search, RoleName role, Boolean isActive, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(search)) {
            String searchPattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), searchPattern),
                    cb.like(cb.lower(root.get("lastName")), searchPattern),
                    cb.like(cb.lower(root.get("email")), searchPattern)
            ));
        }

        if (role != null) {
            spec = spec.and((root, query, cb) -> {
                Join<User, Role> roleJoin = root.join("roles", JoinType.INNER);
                return cb.equal(roleJoin.get("name"), role);
            });
        }

        if (isActive != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), isActive));
        }

        Page<User> usersPage = userRepository.findAll(spec, pageable);
        return PagedResponse.from(usersPage.map(UserResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new DuplicateResourceException("User", "email", request.getEmail().trim());
        }

        Set<Role> roles = new HashSet<>();
        for (RoleName roleName : request.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
            roles.add(role);
        }

        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone())
                .isActive(true)
                .roles(roles)
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(request.getPhone());

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
            if (!request.getIsActive()) {
                refreshTokenRepository.revokeAllTokensByUserId(user.getId());
            }
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> roles = new HashSet<>();
            for (RoleName roleName : request.getRoles()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
                roles.add(role);
            }
            user.setRoles(roles);
        }

        User updatedUser = userRepository.save(user);
        return UserResponse.fromEntity(updatedUser);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Soft delete: deactivate user and revoke tokens
        user.setIsActive(false);
        userRepository.save(user);
        refreshTokenRepository.revokeAllTokensByUserId(user.getId());
    }
}
