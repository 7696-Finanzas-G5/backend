package pe.edu.upc.credibarrio.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.iam.application.UsuarioService;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios")
public class UsuariosController {

    public record CambiarEstadoRequest(@NotNull EstadoActivo estado) {
    }

    public record RestablecerClaveRequest(@NotBlank String nueva) {
    }

    private final UsuarioService usuarios;

    public UsuariosController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public List<UsuarioResponse> listar() {
        return usuarios.listar().stream().map(UsuarioResponse::of).toList();
    }

    @GetMapping("/disponibilidad")
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_NEGOCIO')")
    @Operation(summary = "Indica si un nombre de usuario está libre")
    public Map<String, Boolean> disponibilidad(@RequestParam String username, @RequestParam(required = false) Long exceptId) {
        return Map.of("disponible", !usuarios.isUsernameTaken(username, exceptId));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public UsuarioResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest req) {
        return UsuarioResponse.of(usuarios.cambiarEstado(id, req.estado()));
    }

    @PostMapping("/{id}/restablecer-clave")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseEntity<Void> restablecerClave(@PathVariable Long id, @Valid @RequestBody RestablecerClaveRequest req) {
        usuarios.restablecerClave(id, req.nueva());
        return ResponseEntity.noContent().build();
    }
}
