package pe.edu.upc.credibarrio.catalog.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadPermitida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static pe.edu.upc.credibarrio.shared.domain.DomainException.ensure;

@Entity
@Table(name = "producto_servicio")
public class ProductoServicio {

    public enum TipoItem { PRODUCTO, SERVICIO }

    public static final List<String> UNIDADES_MEDIDA = List.of("Unidad", "Kg", "Litro", "Docena", "Bolsa",
            "Pack x 6 unidades", "Pack x 12 unidades", "Caja x 12", "Pack", "Servicio");

    /** Datos editables del producto o servicio. */
    public record Datos(TipoItem tipoItem, String proveedor, String marca, String descripcion, String unidadMedida,
                        String imagenUrl, BigDecimal precioContado, BigDecimal precioCredito,
                        ModalidadPermitida modalidadPermitida, EstadoActivo estado) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long id;

    @Column(nullable = false)
    private Long idTienda;

    @Column(length = 40)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoItem tipoItem;

    @Column(length = 150)
    private String proveedor;

    @Column(length = 100)
    private String marca;

    @Column(nullable = false, length = 250)
    private String descripcion;

    @Column(nullable = false, length = 50)
    private String unidadMedida;

    /**
     * URL o data URL (base64) de la imagen; el frontend admite hasta 400 KB (~550 000 caracteres en base64).
     * Se usa un texto largo en lugar de {@code @Lob} para que funcione igual en H2, MySQL y PostgreSQL.
     */
    @Column(length = 1_000_000)
    private String imagenUrl;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precioContado;

    /** Precio de lista o al crédito (VA). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precioCredito;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModalidadPermitida modalidadPermitida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoActivo estado;

    protected ProductoServicio() {
    }

    public ProductoServicio(Long idTienda, String codigo, Datos d, boolean exigirImagen) {
        this.idTienda = idTienda;
        this.codigo = codigo;
        actualizar(d, exigirImagen);
    }

    /** Invariantes del catálogo (Ley N° 29571: precio al contado y precio de lista diferenciados). */
    public void actualizar(Datos d, boolean exigirImagen) {
        String desc = d.descripcion() == null ? "" : d.descripcion().trim();
        ensure(d.tipoItem() != null, "El tipo de ítem es obligatorio.");
        ensure(!desc.isEmpty() && desc.length() <= 250, "La descripción es obligatoria (máximo 250 caracteres).");
        ensure(d.unidadMedida() != null && !d.unidadMedida().isBlank(), "La unidad de medida es obligatoria.");
        ensure(!exigirImagen || (d.imagenUrl() != null && !d.imagenUrl().isBlank()), "La imagen del producto es obligatoria.");
        ensure(d.precioContado() != null && d.precioContado().signum() > 0, "El precio al contado debe ser mayor a cero.");
        ensure(d.precioCredito() != null && d.precioCredito().compareTo(d.precioContado()) >= 0,
                "El precio de lista debe ser mayor o igual al precio al contado.");
        ensure(d.modalidadPermitida() != null, "La modalidad permitida es obligatoria.");
        this.tipoItem = d.tipoItem();
        this.proveedor = blankToNull(d.proveedor());
        this.marca = blankToNull(d.marca());
        this.descripcion = desc;
        this.unidadMedida = d.unidadMedida();
        this.imagenUrl = blankToNull(d.imagenUrl());
        this.precioContado = d.precioContado().setScale(2, RoundingMode.HALF_UP);
        this.precioCredito = d.precioCredito().setScale(2, RoundingMode.HALF_UP);
        this.modalidadPermitida = d.modalidadPermitida();
        this.estado = d.estado() == null ? EstadoActivo.ACTIVO : d.estado();
    }

    public boolean permiteModalidad(ModalidadCompra m) {
        return modalidadPermitida == ModalidadPermitida.AMBAS || modalidadPermitida.name().equals(m.name());
    }

    public void cambiarEstado(EstadoActivo estado) {
        this.estado = estado;
    }

    public boolean activo() {
        return estado == EstadoActivo.ACTIVO;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getId() { return id; }
    public Long getIdTienda() { return idTienda; }
    public String getCodigo() { return codigo; }
    public TipoItem getTipoItem() { return tipoItem; }
    public String getProveedor() { return proveedor; }
    public String getMarca() { return marca; }
    public String getDescripcion() { return descripcion; }
    public String getUnidadMedida() { return unidadMedida; }
    public String getImagenUrl() { return imagenUrl; }
    public BigDecimal getPrecioContado() { return precioContado; }
    public BigDecimal getPrecioCredito() { return precioCredito; }
    public ModalidadPermitida getModalidadPermitida() { return modalidadPermitida; }
    public EstadoActivo getEstado() { return estado; }
}
