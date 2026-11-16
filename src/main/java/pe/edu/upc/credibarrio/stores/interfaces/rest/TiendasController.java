package pe.edu.upc.credibarrio.stores.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;
import pe.edu.upc.credibarrio.shared.domain.NotFoundException;
import pe.edu.upc.credibarrio.stores.application.TiendaService;
import pe.edu.upc.credibarrio.stores.application.TiendaService.GuardarTiendaCommand;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda.EstadoTienda;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tiendas")
@Tag(name = "Tiendas")
public class TiendasController {

    public record TiendaRequest(@NotBlank String nombreComercial, @NotBlank String ruc, @NotBlank String direccion,
                                @NotBlank String giroNegocio, boolean activa, @NotBlank String adminUsername,
                                String adminClave) {
        GuardarTiendaCommand toCommand() {
            return new GuardarTiendaCommand(nombreComercial, ruc, direccion, giroNegocio, activa, adminUsername, adminClave);
        }
    }

    public record CambiarEstadoRequest(@NotNull Boolean activa) {
    }

    public record TiendaResponse(Long id, String nombreComercial, String ruc, String direccion, String giroNegocio,
                                 EstadoTienda estado, LocalDateTime fechaRegistro, Long idAdmin, String adminUsername) {
    }

    private final TiendaService tiendas;
    private final CurrentUser currentUser;

    public TiendasController(TiendaService tiendas, CurrentUser currentUser) {
        this.tiendas = tiendas;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public List<TiendaResponse> listar() {
        return tiendas.listar().stream().map(this::toResponse).toList();
    }

    @GetMapping("/giros")
    @Operation(summary = "Giros de negocio admitidos")
    public List<String> giros() {
        return Tienda.GIROS_NEGOCIO;
    }

    @GetMapping("/disponibilidad")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public Map<String, Boolean> disponibilidad(@RequestParam String ruc, @RequestParam(required = false) Long exceptId) {
        return Map.of("disponible", !tiendas.isRucTaken(ruc, exceptId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una tienda (el negocio solo puede ver la suya)")
    public TiendaResponse obtener(@PathVariable Long id) {
        var user = currentUser.require();
        if (user.rol() != Rol.ADMIN_SISTEMA && !id.equals(user.idTienda())) {
            throw new NotFoundException("Tienda no encontrada.");
        }
        return toResponse(tiendas.obtener(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @Operation(summary = "Registra la tienda y su usuario Administrador del Negocio")
    public TiendaResponse registrar(@Valid @RequestBody TiendaRequest req) {
        return toResponse(tiendas.registrar(req.toCommand()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public TiendaResponse actualizar(@PathVariable Long id, @Valid @RequestBody TiendaRequest req) {
        return toResponse(tiendas.actualizar(id, req.toCommand()));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @Operation(summary = "Alta / baja de la tienda (sincroniza el acceso de su administrador)")
    public TiendaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest req) {
        return toResponse(tiendas.cambiarEstado(id, req.activa()));
    }

    private TiendaResponse toResponse(Tienda t) {
        Usuario admin = tiendas.adminDe(t.getId()).orElse(null);
        return new TiendaResponse(t.getId(), t.getNombreComercial(), t.getRuc(), t.getDireccion(), t.getGiroNegocio(),
                t.getEstado(), t.getFechaRegistro(), admin == null ? null : admin.getId(),
                admin == null ? null : admin.getUsername());
    }
}
