package pe.edu.upc.credibarrio.customers.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.credits.application.CreditoService;
import pe.edu.upc.credibarrio.customers.application.ClienteService;
import pe.edu.upc.credibarrio.customers.application.ClienteService.Acceso;
import pe.edu.upc.credibarrio.customers.application.ClienteService.GuardarClienteCommand;
import pe.edu.upc.credibarrio.customers.domain.model.Cliente;
import pe.edu.upc.credibarrio.customers.domain.model.CondicionCredito;
import pe.edu.upc.credibarrio.customers.domain.model.TerminosCredito;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes")
public class ClientesController {

    public record ClienteRequest(@NotBlank String nombres, @NotBlank String apellidos, @NotBlank String dni,
                                 String direccion, String telefono, @NotNull TerminosCredito terminos, boolean activo,
                                 Acceso acceso) {
        GuardarClienteCommand toCommand() {
            return new GuardarClienteCommand(nombres, apellidos, dni, direccion, telefono, terminos, activo, acceso);
        }
    }

    public record CambiarEstadoRequest(@NotNull Boolean activo) {
    }

    /** Cliente con su condición de crédito vigente, su usuario de acceso (si tiene), deuda y crédito disponible. */
    public record ClienteResponse(Long id, Long idTienda, String dni, String nombres, String apellidos,
                                  String direccion, String telefono, String correo, EstadoActivo estado,
                                  LocalDateTime fechaRegistro, Long idUsuario, String username,
                                  CondicionCredito condicionVigente, double deudaVigente, double disponible) {
    }

    private final ClienteService clientes;
    private final CreditoService creditos;

    public ClientesController(ClienteService clientes, CreditoService creditos) {
        this.clientes = clientes;
        this.creditos = creditos;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_NEGOCIO')")
    @Operation(summary = "Clientes de la tienda en sesión (o todos, para el admin del sistema)")
    public List<ClienteResponse> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return clientes.listar().stream().filter(c -> !soloActivos || c.activo()).map(this::toResponse).toList();
    }

    @GetMapping("/disponibilidad")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public Map<String, Boolean> disponibilidad(@RequestParam String dni, @RequestParam(required = false) Long exceptId) {
        return Map.of("disponible", !clientes.isDniTaken(dni, exceptId));
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return toResponse(clientes.obtener(id));
    }

    @GetMapping("/{id}/condiciones")
    @Operation(summary = "Historial de versiones de la condición de crédito (la vigente primero)")
    public List<CondicionCredito> condiciones(@PathVariable Long id) {
        return clientes.historialCondiciones(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Registra el cliente, su línea de crédito y, opcionalmente, su usuario de acceso")
    public ClienteResponse registrar(@Valid @RequestBody ClienteRequest req) {
        return toResponse(clientes.registrar(req.toCommand()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Actualiza el cliente; si cambian los términos se crea una nueva versión de la condición")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest req) {
        return toResponse(clientes.actualizar(id, req.toCommand()));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ClienteResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest req) {
        return toResponse(clientes.cambiarEstado(id, req.activo()));
    }

    private ClienteResponse toResponse(Cliente c) {
        Usuario u = clientes.usuarioDe(c.getId()).orElse(null);
        return new ClienteResponse(c.getId(), c.getIdTienda(), c.getDni(), c.getNombres(), c.getApellidos(),
                c.getDireccion(), c.getTelefono(), c.getCorreo(), c.getEstado(), c.getFechaRegistro(),
                u == null ? null : u.getId(), u == null ? null : u.getUsername(),
                clientes.condicionVigente(c.getId()).orElse(null), creditos.deudaVigente(c.getId()),
                creditos.disponible(c.getId()));
    }
}
