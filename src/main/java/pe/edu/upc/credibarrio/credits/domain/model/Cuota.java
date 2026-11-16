package pe.edu.upc.credibarrio.credits.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.FilaCronograma;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cuota del cronograma francés (pertenece al agregado Compra). */
@Entity
@Table(name = "cuota", uniqueConstraints = @UniqueConstraint(name = "uk_cuota_compra_numero", columnNames = {"id_compra", "numero_cuota"}))
public class Cuota {

    public enum EstadoCuota { PENDIENTE, PAGADA, VENCIDA, ANULADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cuota")
    private Long id;

    @Column(name = "id_compra", nullable = false)
    private Long idCompra;

    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota;

    @Column(nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoInicial;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interesPeriodo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amortizacion;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montoCuota;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoFinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCuota estado;

    protected Cuota() {
    }

    public static Cuota desdeFila(Long idCompra, FilaCronograma f) {
        Cuota q = new Cuota();
        q.idCompra = idCompra;
        q.numeroCuota = f.k();
        q.fechaVencimiento = f.fecha();
        q.saldoInicial = FinancialMath.money(f.saldoInicial());
        q.interesPeriodo = FinancialMath.money(f.interes());
        q.amortizacion = FinancialMath.money(f.amortizacion());
        q.montoCuota = FinancialMath.money(f.cuota());
        q.saldoFinal = FinancialMath.money(f.saldoFinal());
        q.estado = EstadoCuota.PENDIENTE;
        return q;
    }

    public boolean pendiente() {
        return estado == EstadoCuota.PENDIENTE || estado == EstadoCuota.VENCIDA;
    }

    public void marcarPagada() {
        this.estado = EstadoCuota.PAGADA;
    }

    public Long getId() { return id; }
    public Long getIdCompra() { return idCompra; }
    public Integer getNumeroCuota() { return numeroCuota; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public BigDecimal getSaldoInicial() { return saldoInicial; }
    public BigDecimal getInteresPeriodo() { return interesPeriodo; }
    public BigDecimal getAmortizacion() { return amortizacion; }
    public BigDecimal getMontoCuota() { return montoCuota; }
    public BigDecimal getSaldoFinal() { return saldoFinal; }
    public EstadoCuota getEstado() { return estado; }
}
