package pe.edu.upc.credibarrio.customers.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.customers.domain.model.CondicionCredito;
import pe.edu.upc.credibarrio.customers.domain.model.CondicionCredito.EstadoCondicion;

import java.util.List;
import java.util.Optional;

public interface CondicionCreditoRepository extends JpaRepository<CondicionCredito, Long> {

    Optional<CondicionCredito> findFirstByIdClienteAndEstadoOrderByIdDesc(Long idCliente, EstadoCondicion estado);

    List<CondicionCredito> findByIdClienteOrderByIdDesc(Long idCliente);

    default Optional<CondicionCredito> findVigente(Long idCliente) {
        return findFirstByIdClienteAndEstadoOrderByIdDesc(idCliente, EstadoCondicion.VIGENTE);
    }
}
