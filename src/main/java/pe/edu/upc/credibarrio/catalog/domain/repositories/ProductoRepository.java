package pe.edu.upc.credibarrio.catalog.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.catalog.domain.model.ProductoServicio;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;

import java.util.List;

public interface ProductoRepository extends JpaRepository<ProductoServicio, Long> {

    List<ProductoServicio> findByIdTiendaOrderById(Long idTienda);

    List<ProductoServicio> findByIdTiendaAndEstadoOrderById(Long idTienda, EstadoActivo estado);

    long countByIdTiendaAndTipoItem(Long idTienda, ProductoServicio.TipoItem tipoItem);

    long countByIdTiendaAndEstado(Long idTienda, EstadoActivo estado);
}
