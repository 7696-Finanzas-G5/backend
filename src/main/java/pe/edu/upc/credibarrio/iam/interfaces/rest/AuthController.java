package pe.edu.upc.credibarrio.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.iam.application.AuthService;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    public record LoginRequest(@NotNull Rol rol, @NotBlank String username, @NotBlank String clave) {
    }

    public record LoginResponse(String token, String tokenType, UsuarioResponse usuario) {
    }

    public record CambiarClaveRequest(@NotBlank String actual, @NotBlank String nueva) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión con rol, usuario y contraseña; devuelve un JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        var r = auth.login(req.rol(), req.username(), req.clave());
        return new LoginResponse(r.token(), "Bearer", UsuarioResponse.of(r.usuario()));
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario en sesión")
    public UsuarioResponse me() {
        return UsuarioResponse.of(auth.actual());
    }

    @PostMapping("/cambiar-clave")
    public ResponseEntity<Void> cambiarClave(@Valid @RequestBody CambiarClaveRequest req) {
        auth.cambiarClave(req.actual(), req.nueva());
        return ResponseEntity.noContent().build();
    }
}
