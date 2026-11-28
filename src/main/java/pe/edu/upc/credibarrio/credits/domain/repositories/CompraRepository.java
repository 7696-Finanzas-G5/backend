package pe.edu.upc.credibarrio.credits.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.Compra.EstadoCompra;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;

import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    List<Compra> findByIdClienteOrderByFechaHoraCompraDesc(Long idCliente);

    List<Compra> findByIdClienteAndModalidadAndEstadoOrderByFechaHoraCompra(Long idCliente, ModalidadCompra modalidad,
                                                                           EstadoCompra estado);

    List<Compra> findByIdClienteInOrderByFechaHoraCompraDesc(Collection<Long> idsCliente, Pageable pageable);

    List<Compra> findByIdClienteAndEstado(Long idCliente, EstadoCompra estado);

    List<Compra> findByModalidadAndEstadoOrderByFechaHoraCompra(ModalidadCompra modalidad, EstadoCompra estado);
}
