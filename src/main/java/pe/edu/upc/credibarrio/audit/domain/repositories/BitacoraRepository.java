package pe.edu.upc.credibarrio.audit.domain.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.audit.domain.model.RegistroAuditoria;

import java.util.Collection;
import java.util.List;

public interface BitacoraRepository extends JpaRepository<RegistroAuditoria, Long> {

    List<RegistroAuditoria> findAllByOrderByIdDesc(Pageable pageable);

    List<RegistroAuditoria> findByIdUsuarioInOrderByIdDesc(Collection<Long> idsUsuario, Pageable pageable);
}
