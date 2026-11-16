package pe.edu.upc.credibarrio.customers.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.customers.domain.model.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByIdTiendaOrderById(Long idTienda);

    Optional<Cliente> findByIdTiendaAndDni(Long idTienda, String dni);
}
