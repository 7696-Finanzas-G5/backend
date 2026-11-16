package pe.edu.upc.credibarrio.credits.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "detalle_compra")
public class DetalleCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long id;

    @Column(nullable = false)
    private Long idProducto;

    @Column(nullable = false, length = 250)
    private String descripcionItem;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precioUnitarioCredito;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    protected DetalleCompra() {
    }

    public DetalleCompra(Long idProducto, String descripcionItem, int cantidad, BigDecimal precioUnitarioCredito,
                         BigDecimal subtotal) {
        this.idProducto = idProducto;
        this.descripcionItem = descripcionItem;
        this.cantidad = cantidad;
        this.precioUnitarioCredito = precioUnitarioCredito;
        this.subtotal = subtotal;
    }

    public Long getId() { return id; }
    public Long getIdProducto() { return idProducto; }
    public String getDescripcionItem() { return descripcionItem; }
    public Integer getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitarioCredito() { return precioUnitarioCredito; }
    public BigDecimal getSubtotal() { return subtotal; }
}
