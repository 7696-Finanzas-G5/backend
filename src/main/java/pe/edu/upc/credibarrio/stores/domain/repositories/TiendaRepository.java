package pe.edu.upc.credibarrio.stores.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda;

import java.util.Optional;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {

    Optional<Tienda> findByRuc(String ruc);

    long countByEstado(Tienda.EstadoTienda estado);
}
