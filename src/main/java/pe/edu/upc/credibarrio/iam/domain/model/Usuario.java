package pe.edu.upc.credibarrio.iam.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

import java.time.LocalDateTime;

import static pe.edu.upc.credibarrio.shared.domain.DomainException.ensure;

@Entity
@Table(name = "usuario")
public class Usuario {

    public static final int CLAVE_MIN = 8;
    public static final int CLAVE_MAX = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    private Long idTienda;

    private Long idCliente;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 100)
    private String claveHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoActivo estado;

    private LocalDateTime ultimoAcceso;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    protected Usuario() {
    }

    public Usuario(String username, String claveHash, Rol rol, Long idTienda, Long idCliente, EstadoActivo estado) {
        validarAsociacion(rol, idTienda, idCliente);
        this.username = validarUsername(username);
        this.claveHash = claveHash;
        this.rol = rol;
        this.idTienda = idTienda;
        this.idCliente = idCliente;
        this.estado = estado;
        this.fechaRegistro = LocalDateTime.now();
    }

    // ------------------------------------------------------------ invariantes

    public static String validarUsername(String username) {
        String u = username == null ? "" : username.trim();
        ensure(u.length() >= 4 && u.length() <= 80, "El usuario debe tener entre 4 y 80 caracteres.");
        return u;
    }

    public static void validarClave(String clave) {
        ensure(clave != null && clave.length() >= CLAVE_MIN && clave.length() <= CLAVE_MAX,
                "La contraseña debe tener entre " + CLAVE_MIN + " y " + CLAVE_MAX + " caracteres.");
    }

    /** Invariantes de asociación según el rol. */
    public static void validarAsociacion(Rol rol, Long idTienda, Long idCliente) {
        switch (rol) {
            case ADMIN_SISTEMA -> ensure(idTienda == null && idCliente == null,
                    "El administrador del sistema no pertenece a una tienda.");
            case ADMIN_NEGOCIO -> ensure(idTienda != null && idCliente == null,
                    "El administrador del negocio debe estar asociado a una tienda.");
            case CLIENTE -> ensure(idTienda != null && idCliente != null,
                    "El usuario cliente debe estar asociado a un cliente.");
        }
    }

    // --------------------------------------------------------------- comandos

    public void cambiarUsername(String username) {
        this.username = validarUsername(username);
    }

    public void cambiarClaveHash(String claveHash) {
        this.claveHash = claveHash;
    }

    public void cambiarEstado(EstadoActivo estado) {
        this.estado = estado;
    }

    public void registrarAcceso() {
        this.ultimoAcceso = LocalDateTime.now();
    }

    public boolean activo() {
        return estado == EstadoActivo.ACTIVO;
    }

    public Long getId() { return id; }
    public Long getIdTienda() { return idTienda; }
    public Long getIdCliente() { return idCliente; }
    public String getUsername() { return username; }
    public String getClaveHash() { return claveHash; }
    public Rol getRol() { return rol; }
    public EstadoActivo getEstado() { return estado; }
    public LocalDateTime getUltimoAcceso() { return ultimoAcceso; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
