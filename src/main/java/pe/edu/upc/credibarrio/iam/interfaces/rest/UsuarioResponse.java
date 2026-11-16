package pe.edu.upc.credibarrio.iam.interfaces.rest;

import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

import java.time.LocalDateTime;

/** Vista pública del usuario (nunca expone el hash de la contraseña). */
public record UsuarioResponse(Long id, Long idTienda, Long idCliente, String username, Rol rol, EstadoActivo estado,
                              LocalDateTime ultimoAcceso, LocalDateTime fechaRegistro) {

    public static UsuarioResponse of(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getIdTienda(), u.getIdCliente(), u.getUsername(), u.getRol(),
                u.getEstado(), u.getUltimoAcceso(), u.getFechaRegistro());
    }
}
