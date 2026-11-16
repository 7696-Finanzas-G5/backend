package pe.edu.upc.credibarrio.credits.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.credits.domain.model.Cuota;

import java.util.Collection;
import java.util.List;

public interface CuotaRepository extends JpaRepository<Cuota, Long> {

    List<Cuota> findByIdCompraOrderByNumeroCuota(Long idCompra);

    List<Cuota> findByIdCompraInOrderByNumeroCuota(Collection<Long> idsCompra);
}
