package pe.edu.upc.credibarrio.billing.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.Imputacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Pago total único (no se admiten pagos parciales), imputado por prelación. */
@Entity
@Table(name = "pago")
public class Pago {

    public enum EstadoPago { REGISTRADO, ANULADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    private Long idLiquidacion;

    private Long idCuota;

    private Long idUsuarioRegistro;

    @Column(nullable = false)
    private LocalDateTime fechaHoraPago;

    @Column(nullable = false)
    private Integer diasMora;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal capitalPagado;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interesCompensatorioPagado;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interesMoratorioPagado;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montoTotalPagado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoPago estado;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    protected Pago() {
    }

    public Pago(Long idCliente, Long idLiquidacion, Long idCuota, Long idUsuarioRegistro, LocalDate fechaPago,
                int diasMora, Imputacion imp) {
        this.idCliente = idCliente;
        this.idLiquidacion = idLiquidacion;
        this.idCuota = idCuota;
        this.idUsuarioRegistro = idUsuarioRegistro;
        this.fechaHoraPago = fechaPago.atStartOfDay();
        this.diasMora = diasMora;
        this.capitalPagado = FinancialMath.money(imp.capital());
        this.interesCompensatorioPagado = FinancialMath.money(imp.compensatorio());
        this.interesMoratorioPagado = FinancialMath.money(imp.mora());
        this.montoTotalPagado = FinancialMath.money(imp.total());
        this.estado = EstadoPago.REGISTRADO;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getIdCliente() { return idCliente; }
    public Long getIdLiquidacion() { return idLiquidacion; }
    public Long getIdCuota() { return idCuota; }
    public Long getIdUsuarioRegistro() { return idUsuarioRegistro; }
    public LocalDateTime getFechaHoraPago() { return fechaHoraPago; }
    public Integer getDiasMora() { return diasMora; }
    public BigDecimal getCapitalPagado() { return capitalPagado; }
    public BigDecimal getInteresCompensatorioPagado() { return interesCompensatorioPagado; }
    public BigDecimal getInteresMoratorioPagado() { return interesMoratorioPagado; }
    public BigDecimal getMontoTotalPagado() { return montoTotalPagado; }
    public EstadoPago getEstado() { return estado; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
