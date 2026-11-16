package pe.edu.upc.credibarrio.credits.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Aggregate root: operación de venta al crédito. */
@Entity
@Table(name = "compra")
public class Compra {

    public enum EstadoCompra { REGISTRADA, CANCELADA, ANULADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_compra")
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    /** Versión de la condición de crédito con la que se pactó la compra. */
    @Column(nullable = false)
    private Long idCondicion;

    @Column(nullable = false)
    private LocalDateTime fechaHoraCompra;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModalidadCompra modalidad;

    private Integer numeroCuotas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoCompra estado;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    /** Capital financiado (VA = Σ subtotales a precio de lista). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal capital;

    /** Fin de mes: fecha de pago del ciclo. Cuotas: fin del período de gracia total. */
    @Column(nullable = false)
    private LocalDate fechaPago;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_compra", nullable = false)
    private List<DetalleCompra> detalle = new ArrayList<>();

    protected Compra() {
    }

    public Compra(Long idCliente, Long idCondicion, LocalDateTime fechaHoraCompra, ModalidadCompra modalidad,
                  Integer numeroCuotas, BigDecimal capital, LocalDate fechaPago, List<DetalleCompra> detalle) {
        this.idCliente = idCliente;
        this.idCondicion = idCondicion;
        this.fechaHoraCompra = fechaHoraCompra;
        this.modalidad = modalidad;
        this.numeroCuotas = modalidad == ModalidadCompra.CUOTAS ? numeroCuotas : null;
        this.capital = capital;
        this.fechaPago = fechaPago;
        this.estado = EstadoCompra.REGISTRADA;
        this.fechaRegistro = LocalDateTime.now();
        this.detalle.addAll(detalle);
    }

    public String descripcion() {
        return detalle.stream()
                .map(d -> d.getDescripcionItem() + (d.getCantidad() > 1 ? " x" + d.getCantidad() : ""))
                .collect(Collectors.joining(", "));
    }

    public boolean vigente() {
        return estado == EstadoCompra.REGISTRADA;
    }

    public void cancelar() {
        this.estado = EstadoCompra.CANCELADA;
    }

    /** Ajusta la fecha de registro (usado por la carga de datos de demostración). */
    public void fechaRegistro(LocalDateTime fecha) {
        this.fechaRegistro = fecha;
    }

    public Long getId() { return id; }
    public Long getIdCliente() { return idCliente; }
    public Long getIdCondicion() { return idCondicion; }
    public LocalDateTime getFechaHoraCompra() { return fechaHoraCompra; }
    public ModalidadCompra getModalidad() { return modalidad; }
    public Integer getNumeroCuotas() { return numeroCuotas; }
    public EstadoCompra getEstado() { return estado; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public BigDecimal getCapital() { return capital; }
    public LocalDate getFechaPago() { return fechaPago; }
    public List<DetalleCompra> getDetalle() { return detalle; }
}
