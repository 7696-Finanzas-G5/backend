package pe.edu.upc.credibarrio.audit.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Registro inalterable de la bitácora de auditoría (audit trail, COBIT 2019). */
@Entity
@Table(name = "bitacora_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bitacora")
    private Long id;

    private Long idUsuario;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(nullable = false, length = 60)
    private String entidad;

    private Long idRegistro;

    @Column(length = 500)
    private String detalle;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Long idUsuario, String accion, String entidad, Long idRegistro, String detalle) {
        this.idUsuario = idUsuario;
        this.accion = accion;
        this.entidad = entidad;
        this.idRegistro = idRegistro;
        this.detalle = detalle != null && detalle.length() > 500 ? detalle.substring(0, 500) : detalle;
        this.fechaHora = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getIdUsuario() { return idUsuario; }
    public String getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public Long getIdRegistro() { return idRegistro; }
    public String getDetalle() { return detalle; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
