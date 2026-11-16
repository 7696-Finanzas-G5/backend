package pe.edu.upc.credibarrio.billing.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.billing.domain.model.Pago;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByIdClienteOrderByIdDesc(Long idCliente);
}
