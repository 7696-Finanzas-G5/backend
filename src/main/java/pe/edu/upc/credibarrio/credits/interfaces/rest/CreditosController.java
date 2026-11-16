package pe.edu.upc.credibarrio.credits.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.credits.application.CreditoService;
import pe.edu.upc.credibarrio.credits.application.CreditoService.ResumenCredito;
import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.Cuota;
import pe.edu.upc.credibarrio.credits.domain.services.OriginacionCredito.SimulacionCompra;
import pe.edu.upc.credibarrio.credits.domain.services.OriginacionCredito.SolicitudCompra;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Créditos")
public class CreditosController {

    /** {@code fechaHoraCompra} opcional (por defecto, ahora); {@code n} requerido solo en modalidad CUOTAS. */
    public record CompraRequest(@NotNull Long idCliente, @NotNull Long idProducto, @NotNull @Min(1) Integer cantidad,
                                LocalDateTime fechaHoraCompra, @NotNull ModalidadCompra modalidad, Integer n) {
        SolicitudCompra toSolicitud() {
            return new SolicitudCompra(idCliente, idProducto, cantidad, fechaHoraCompra, modalidad, n);
        }
    }

    private final CreditoService creditos;
    private final CurrentUser currentUser;

    public CreditosController(CreditoService creditos, CurrentUser currentUser) {
        this.creditos = creditos;
        this.currentUser = currentUser;
    }

    @GetMapping("/compras")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Compras más recientes de los clientes de la tienda en sesión")
    public List<Compra> recientes(@RequestParam(defaultValue = "20") int limite) {
        return creditos.recientesDeMiTienda(currentUser.tiendaId(), limite);
    }

    @GetMapping("/clientes/{idCliente}/credito")
    @Operation(summary = "Condiciones vigentes, deuda vigente y crédito disponible del cliente")
    public ResumenCredito resumen(@PathVariable Long idCliente) {
        return creditos.resumen(idCliente);
    }

    @GetMapping("/clientes/{idCliente}/compras")
    public List<Compra> compras(@PathVariable Long idCliente) {
        return creditos.comprasDe(idCliente);
    }

    @PostMapping("/compras/simulacion")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Simula la compra: valida reglas y proyecta el pago a fin de mes o el cronograma francés")
    public SimulacionCompra simular(@Valid @RequestBody CompraRequest req) {
        return creditos.simular(req.toSolicitud());
    }

    @PostMapping("/compras")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Registra la compra al crédito (y su cronograma si es en cuotas)")
    public Compra registrar(@Valid @RequestBody CompraRequest req) {
        return creditos.registrarCompra(req.toSolicitud());
    }

    @GetMapping("/compras/{id}")
    public Compra obtener(@PathVariable Long id) {
        return creditos.obtenerCompra(id);
    }

    @GetMapping("/compras/{id}/cuotas")
    @Operation(summary = "Cronograma de cuotas de la compra")
    public List<Cuota> cuotas(@PathVariable Long id) {
        return creditos.cuotasDeCompra(id);
    }
}
