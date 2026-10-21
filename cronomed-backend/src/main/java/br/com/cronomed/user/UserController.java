package br.com.cronomed.user;

import br.com.cronomed.auth.AuthService;
import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.user.UserDtos.ChangePasswordRequest;
import br.com.cronomed.user.UserDtos.DeleteAccountRequest;
import br.com.cronomed.user.UserDtos.PushTokenRequest;
import br.com.cronomed.user.UserDtos.UpdateProfileRequest;
import br.com.cronomed.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService users;
    private final AuthService auth;

    public UserController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @GetMapping
    public UserResponse me() {
        return UserResponse.from(users.get(CurrentUser.userId()));
    }

    @PutMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return UserResponse.from(users.update(CurrentUser.userId(), req));
    }

    /** Troca de senha: encerra todas as sessoes (o usuario precisa entrar novamente). */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(CurrentUser.userId(), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Registra o token FCM deste aparelho para receber notificacoes push. */
    @PutMapping("/push-token")
    public ResponseEntity<Void> pushToken(@Valid @RequestBody PushTokenRequest req) {
        users.registerPushToken(CurrentUser.userId(), req);
        return ResponseEntity.noContent().build();
    }

    /** Exclusao definitiva da conta e dos dados (LGPD). */
    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequest req) {
        users.deleteAccount(CurrentUser.userId(), req.password());
        return ResponseEntity.noContent().build();
    }
}
