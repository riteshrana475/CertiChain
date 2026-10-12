package com.certichain.auth;

import com.certichain.institution.InstitutionEntity;
import com.certichain.institution.InstitutionRepository;
import com.certichain.security.CurrentUser;
import com.certichain.security.JwtService;
import com.certichain.user.UserAccount;
import com.certichain.user.UserAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

    private final UserAccountRepository users;
    private final InstitutionRepository institutions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser current;

    public AuthController(
            UserAccountRepository users,
            InstitutionRepository institutions,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUser current) {
        this.users = users;
        this.institutions = institutions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.current = current;
    }

    @PostMapping("/api/auth/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }
        String role = request.role() == null || request.role().isBlank() ? "INSTITUTION_ADMIN" : request.role().trim();
        if (!role.equals("STUDENT") && !role.equals("RECRUITER") && !role.equals("INSTITUTION_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose student, recruiter, or institution admin.");
        }
        requirePassword(request.password());
        if (!"INSTITUTION_ADMIN".equals(role)) {
            UserAccount user = newAccount(request.fullName().trim(), email, request.password(), null, role);
            users.save(user);
            return response(user, "");
        }
        String institutionName = request.institutionName() == null ? "" : request.institutionName().trim();
        if (institutionName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add the institution name.");
        }
        if (institutions.findByNameIgnoreCase(institutionName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An institution with that name already exists.");
        }
        InstitutionEntity institution = new InstitutionEntity();
        institution.setId(UUID.randomUUID());
        institution.setName(institutionName);
        institutions.save(institution);
        UserAccount user = newAccount(request.fullName().trim(), email, request.password(), institution.getId(), "INSTITUTION_ADMIN");
        users.save(user);
        return response(user, institution.getName());
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That email or password does not match."));
        return response(user, institutionName(user));
    }

    @GetMapping("/api/auth/me")
    public LoginResponse me() {
        UserAccount user = current.require();
        return new LoginResponse(null, user.getFullName(), user.getEmail(), user.getRole(), institutionName(user));
    }

    @PostMapping("/api/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordRequest request) {
        UserAccount user = current.require();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current password does not match.");
        }
        requirePassword(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
    }

    private LoginResponse response(UserAccount user, String institutionName) {
        return new LoginResponse(jwtService.issue(user.getEmail()), user.getFullName(), user.getEmail(), user.getRole(), institutionName);
    }

    private String institutionName(UserAccount user) {
        if (user.getInstitutionId() == null) {
            return "";
        }
        return institutions.findById(user.getInstitutionId()).map(InstitutionEntity::getName).orElse("");
    }

    private UserAccount newAccount(String name, String email, String password, UUID institutionId, String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setInstitutionId(institutionId);
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }

    private static void requirePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password of at least 8 characters.");
        }
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            String institutionName,
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String password,
            String role) {
    }

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {
    }

    public record LoginResponse(String token, String name, String email, String role, String institutionName) {
    }
}
