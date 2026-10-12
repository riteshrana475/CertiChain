package com.certichain.security;

import com.certichain.institution.InstitutionEntity;
import com.certichain.institution.InstitutionRepository;
import com.certichain.user.UserAccount;
import com.certichain.user.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {

    private final UserAccountRepository users;
    private final InstitutionRepository institutions;

    public CurrentUser(UserAccountRepository users, InstitutionRepository institutions) {
        this.users = users;
        this.institutions = institutions;
    }

    public UserAccount require() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
    }

    public InstitutionEntity institution() {
        UserAccount user = require();
        if (!isIssuer(user) || user.getInstitutionId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an institution admin can do that.");
        }
        return institutions.findById(user.getInstitutionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is not linked to an institution."));
    }

    public UserAccount requireRole(String... roles) {
        UserAccount user = require();
        for (String role : roles) {
            if (role.equals(user.getRole())) {
                return user;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to that.");
    }

    private static boolean isIssuer(UserAccount user) {
        return "INSTITUTION_ADMIN".equals(user.getRole()) || "PLATFORM_ADMIN".equals(user.getRole());
    }
}
