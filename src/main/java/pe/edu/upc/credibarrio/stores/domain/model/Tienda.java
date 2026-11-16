package pe.edu.upc.credibarrio.stores.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static pe.edu.upc.credibarrio.shared.domain.DomainException.ensure;

@Entity
@Table(name = "tienda")
public class Tienda {

    public enum EstadoTienda { ACTIVA, INACTIVA }

    public static final List<String> GIROS_NEGOCIO = List.of("Bodega", "Supermercado de Barrio", "Frutería",
            "Carnicería", "Pollería", "Panadería", "Bazar", "Licorería", "Peluquería", "Spa", "Otro");

    public static final Pattern RUC_PATTERN = Pattern.compile("^(10|15|17|20)\\d{9}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tienda")
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombreComercial;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(nullable = false, length = 250)
    private String direccion;

    @Column(nullable = false, length = 50)
    private String giroNegocio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoTienda estado;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    protected Tienda() {
    }

    public Tienda(String nombreComercial, String ruc, String direccion, String giroNegocio, EstadoTienda estado) {
        actualizarDatos(nombreComercial, ruc, direccion, giroNegocio);
        this.estado = estado;
        this.fechaRegistro = LocalDateTime.now();
    }

    public void actualizarDatos(String nombreComercial, String ruc, String direccion, String giroNegocio) {
        String nombre = nombreComercial == null ? "" : nombreComercial.trim();
        String dir = direccion == null ? "" : direccion.trim();
        ensure(!nombre.isEmpty() && nombre.length() <= 120, "El nombre comercial es obligatorio (máximo 120 caracteres).");
        ensure(ruc != null && RUC_PATTERN.matcher(ruc).matches(), "El RUC debe tener 11 dígitos e iniciar con 10, 15, 17 o 20.");
        ensure(!dir.isEmpty() && dir.length() <= 250, "La dirección es obligatoria (máximo 250 caracteres).");
        ensure(GIROS_NEGOCIO.contains(giroNegocio), "Giro de negocio no válido.");
        this.nombreComercial = nombre;
        this.ruc = ruc;
        this.direccion = dir;
        this.giroNegocio = giroNegocio;
    }

    public void cambiarEstado(EstadoTienda estado) {
        this.estado = estado;
    }

    public boolean activa() {
        return estado == EstadoTienda.ACTIVA;
    }

    public Long getId() { return id; }
    public String getNombreComercial() { return nombreComercial; }
    public String getRuc() { return ruc; }
    public String getDireccion() { return direccion; }
    public String getGiroNegocio() { return giroNegocio; }
    public EstadoTienda getEstado() { return estado; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
