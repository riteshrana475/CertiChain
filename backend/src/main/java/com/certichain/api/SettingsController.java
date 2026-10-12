package com.certichain.api;

import com.certichain.blockchain.ChainService;
import com.certichain.crypto.CredentialSigner;
import com.certichain.crypto.SigningKeyService;
import com.certichain.security.CurrentUser;
import com.certichain.user.UserAccount;
import com.certichain.user.UserAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SettingsController {

    private final ChainService chain;
    private final CurrentUser current;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CredentialSigner signer;
    private final SigningKeyService signingKeys;

    public SettingsController(
            ChainService chain,
            CurrentUser current,
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            CredentialSigner signer,
            SigningKeyService signingKeys) {
        this.chain = chain;
        this.current = current;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.signer = signer;
        this.signingKeys = signingKeys;
    }

    @GetMapping("/api/settings/signing")
    public SigningStatus signing() {
        current.institution();
        boolean configured = signer.configured();
        return new SigningStatus(configured, configured ? signingKeys.publicKeyId() : "");
    }

    @GetMapping("/api/settings/chain")
    public ChainService.ChainView chain() {
        current.require();
        return chain.view();
    }

    @PostMapping("/api/settings/chain")
    public ChainService.ChainView saveChain(@Valid @RequestBody ChainRequest request) {
        current.require();
        try {
            return chain.configure(request.rpcUrl(), request.chainId(), request.privateKey());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/api/accounts")
    public List<AccountResponse> accounts() {
        return users.findByInstitutionIdOrderByFullNameAsc(current.institution().getId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping("/api/accounts")
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {
        String email = request.email().trim();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }
        if (request.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password of at least 8 characters.");
        }
        UserAccount user = new UserAccount();
        user.setId(java.util.UUID.randomUUID());
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setRole("INSTITUTION_ADMIN");
        user.setInstitutionId(current.institution().getId());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return AccountResponse.from(users.save(user));
    }

    public record SigningStatus(boolean configured, String keyId) {
    }

    public record ChainRequest(@NotBlank String rpcUrl, long chainId, String privateKey) {
    }

    public record AccountRequest(
            @NotBlank String fullName, @Email @NotBlank String email, @NotBlank @Size(min = 8) String password) {
    }

    public record AccountResponse(String id, String name, String email) {
        static AccountResponse from(UserAccount user) {
            return new AccountResponse(user.getId().toString(), user.getFullName(), user.getEmail());
        }
    }
}
