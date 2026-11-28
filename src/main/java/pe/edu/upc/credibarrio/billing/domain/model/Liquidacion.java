package pe.edu.upc.credibarrio.billing.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Listado de pago generado en la fecha de corte (modalidad fin de mes). */
@Entity
@Table(name = "liquidacion")
public class Liquidacion {

    public enum EstadoLiquidacion { PENDIENTE, PAGADA, VENCIDA, ANULADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_liquidacion")
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    @Column(nullable = false)
    /** Condición de referencia para el último corte; cada detalle conserva su pacto mediante idCompra. */
    private Long idCondicion;

    @Column(nullable = false)
    private LocalDate fechaCorte;

    @Column(nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal capitalTotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interesCompensatorioTotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalExigible;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoLiquidacion estado;

    @Column(nullable = false)
    private LocalDateTime fechaGeneracion;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_liquidacion", nullable = false)
    private List<LiquidacionDetalle> detalle = new ArrayList<>();

    protected Liquidacion() {
    }

    public Liquidacion(Long idCliente, Long idCondicion, LocalDate fechaCorte, LocalDate fechaVencimiento,
                       BigDecimal capitalTotal, BigDecimal interesCompensatorioTotal, BigDecimal totalExigible,
                       EstadoLiquidacion estado, List<LiquidacionDetalle> detalle) {
        this.idCliente = idCliente;
        this.idCondicion = idCondicion;
        this.fechaCorte = fechaCorte;
        this.fechaVencimiento = fechaVencimiento;
        this.capitalTotal = capitalTotal;
        this.interesCompensatorioTotal = interesCompensatorioTotal;
        this.totalExigible = totalExigible;
        this.estado = estado;
        this.fechaGeneracion = LocalDateTime.now();
        this.detalle.addAll(detalle);
    }

    /** El listado sigue pendiente de cobro (generado al corte, vigente o ya vencido). */
    public boolean porCobrar() {
        return estado == EstadoLiquidacion.PENDIENTE || estado == EstadoLiquidacion.VENCIDA;
    }

    /** Pasada la fecha de pago pactada sin cancelar, el listado queda vencido (genera mora). */
    public void marcarVencida() {
        if (estado == EstadoLiquidacion.PENDIENTE) this.estado = EstadoLiquidacion.VENCIDA;
    }

    public void marcarPagada() {
        this.estado = EstadoLiquidacion.PAGADA;
    }

    public Long getId() { return id; }
    public Long getIdCliente() { return idCliente; }
    public Long getIdCondicion() { return idCondicion; }
    public LocalDate getFechaCorte() { return fechaCorte; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public BigDecimal getCapitalTotal() { return capitalTotal; }
    public BigDecimal getInteresCompensatorioTotal() { return interesCompensatorioTotal; }
    public BigDecimal getTotalExigible() { return totalExigible; }
    public EstadoLiquidacion getEstado() { return estado; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public List<LiquidacionDetalle> getDetalle() { return detalle; }
}
