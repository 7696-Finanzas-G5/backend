package pe.edu.upc.credibarrio.customers.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;

import java.time.LocalDateTime;

import static pe.edu.upc.credibarrio.shared.domain.DomainException.ensure;

@Entity
@Table(name = "cliente", uniqueConstraints = @UniqueConstraint(name = "uk_cliente_tienda_dni", columnNames = {"id_tienda", "dni"}))
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Long id;

    @Column(name = "id_tienda", nullable = false)
    private Long idTienda;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 120)
    private String apellidos;

    @Column(length = 250)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Column(length = 120)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoActivo estado;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    protected Cliente() {
    }

    public Cliente(Long idTienda, String nombres, String apellidos, String dni, String direccion, String telefono,
                   EstadoActivo estado) {
        this.idTienda = idTienda;
        actualizarDatos(nombres, apellidos, dni, direccion, telefono);
        this.estado = estado;
        this.fechaRegistro = LocalDateTime.now();
    }

    public void actualizarDatos(String nombres, String apellidos, String dni, String direccion, String telefono) {
        String n = nombres == null ? "" : nombres.trim();
        String a = apellidos == null ? "" : apellidos.trim();
        ensure(!n.isEmpty() && n.length() <= 100, "Los nombres son obligatorios (máximo 100 caracteres).");
        ensure(!a.isEmpty() && a.length() <= 120, "Los apellidos son obligatorios (máximo 120 caracteres).");
        ensure(dni != null && dni.matches("^\\d{8}$"), "El DNI debe tener 8 dígitos.");
        this.nombres = n;
        this.apellidos = a;
        this.dni = dni;
        this.direccion = direccion == null || direccion.isBlank() ? null : direccion.trim();
        this.telefono = telefono == null || telefono.isBlank() ? null : telefono.trim();
    }

    public void cambiarEstado(EstadoActivo estado) {
        this.estado = estado;
    }

    public boolean activo() {
        return estado == EstadoActivo.ACTIVO;
    }

    public String nombreCompleto() {
        return (nombres + " " + apellidos).trim();
    }

    public Long getId() { return id; }
    public Long getIdTienda() { return idTienda; }
    public String getDni() { return dni; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getDireccion() { return direccion; }
    public String getTelefono() { return telefono; }
    public String getCorreo() { return correo; }
    public EstadoActivo getEstado() { return estado; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
