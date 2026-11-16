package pe.edu.upc.credibarrio.billing.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion;

import java.util.List;

public interface LiquidacionRepository extends JpaRepository<Liquidacion, Long> {

    List<Liquidacion> findByIdClienteOrderByIdDesc(Long idCliente);
}
