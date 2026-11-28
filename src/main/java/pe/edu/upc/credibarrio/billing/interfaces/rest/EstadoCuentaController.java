package pe.edu.upc.credibarrio.billing.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService.CuotaConMora;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService.EstadoCiclo;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService.ResultadoPago;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService.ResultadoCorte;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion;
import pe.edu.upc.credibarrio.billing.domain.model.Pago;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Estado de cuenta y pagos")
public class EstadoCuentaController {

    /** {@code fechaEfectiva} opcional: por defecto la fecha pactada (ciclo) o hoy (cuota). */
    public record PagoRequest(LocalDate fechaEfectiva, @NotNull @Positive Double monto) {
    }

    private final EstadoCuentaService estadoCuenta;

    public EstadoCuentaController(EstadoCuentaService estadoCuenta) {
        this.estadoCuenta = estadoCuenta;
    }

    @GetMapping("/clientes/{idCliente}/estado-cuenta/ciclos")
    @Operation(summary = "Fechas de pago con compras a fin de mes pendientes")
    public List<LocalDate> ciclos(@PathVariable Long idCliente) {
        return estadoCuenta.ciclosPendientes(idCliente);
    }

    @GetMapping("/clientes/{idCliente}/estado-cuenta/ciclos/{fechaPago}")
    @Operation(summary = "Listado de pago del ciclo con interés compensatorio y moratorio a la fecha efectiva")
    public ResponseEntity<EstadoCiclo> ciclo(@PathVariable Long idCliente,
                                             @PathVariable @DateTimeFormat(iso = DATE) LocalDate fechaPago,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaEfectiva) {
        EstadoCiclo e = estadoCuenta.estadoCiclo(idCliente, fechaPago, fechaEfectiva);
        return e == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(e);
    }

    @PostMapping("/clientes/{idCliente}/estado-cuenta/ciclos/{fechaPago}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Registra el pago total del ciclo (no se admiten pagos parciales)")
    public ResultadoPago pagarCiclo(@PathVariable Long idCliente,
                                    @PathVariable @DateTimeFormat(iso = DATE) LocalDate fechaPago,
                                    @Valid @RequestBody PagoRequest req) {
        return estadoCuenta.pagarCiclo(idCliente, fechaPago, req.fechaEfectiva(), req.monto());
    }

    @PostMapping("/clientes/{idCliente}/estado-cuenta/ciclos/{fechaPago}/listado")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Genera el listado de pago del ciclo (también lo hace el proceso automático de corte)")
    public Liquidacion generarListado(@PathVariable Long idCliente,
                                      @PathVariable @DateTimeFormat(iso = DATE) LocalDate fechaPago) {
        return estadoCuenta.generarListado(idCliente, fechaPago);
    }

    @PostMapping("/cortes/ejecutar")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @Operation(summary = "Ejecuta ahora el proceso de corte: genera listados pendientes y marca los vencidos")
    public ResultadoCorte ejecutarCorte() {
        return estadoCuenta.ejecutarCorte();
    }

    @GetMapping("/clientes/{idCliente}/estado-cuenta/cuotas-pendientes")
    @Operation(summary = "Cuotas pendientes con la mora a la fecha efectiva (solo la más antigua de cada compra es pagable)")
    public List<CuotaConMora> cuotasPendientes(@PathVariable Long idCliente,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaEfectiva) {
        return estadoCuenta.cuotasPendientes(idCliente, fechaEfectiva);
    }

    @PostMapping("/cuotas/{idCuota}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    @Operation(summary = "Registra el pago de una cuota (en orden y por el total exigible)")
    public ResultadoPago pagarCuota(@PathVariable Long idCuota, @Valid @RequestBody PagoRequest req) {
        return estadoCuenta.pagarCuota(idCuota, req.fechaEfectiva(), req.monto());
    }

    @GetMapping("/clientes/{idCliente}/pagos")
    public List<Pago> pagos(@PathVariable Long idCliente) {
        return estadoCuenta.pagosDe(idCliente);
    }

    @GetMapping("/clientes/{idCliente}/liquidaciones")
    public List<Liquidacion> liquidaciones(@PathVariable Long idCliente) {
        return estadoCuenta.liquidacionesDe(idCliente);
    }
}
