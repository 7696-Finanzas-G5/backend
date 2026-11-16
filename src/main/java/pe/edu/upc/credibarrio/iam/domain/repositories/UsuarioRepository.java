package pe.edu.upc.credibarrio.iam.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    Optional<Usuario> findFirstByIdTiendaAndRol(Long idTienda, Rol rol);

    Optional<Usuario> findByIdCliente(Long idCliente);

    List<Usuario> findByIdTienda(Long idTienda);
}
