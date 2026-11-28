package pe.edu.upc.credibarrio.billing.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion;

import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion.EstadoLiquidacion;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiquidacionRepository extends JpaRepository<Liquidacion, Long> {

    List<Liquidacion> findByIdClienteOrderByIdDesc(Long idCliente);

    boolean existsByIdClienteAndFechaVencimientoAndEstadoNot(Long idCliente, LocalDate fechaVencimiento,
                                                          EstadoLiquidacion estado);

    Optional<Liquidacion> findFirstByIdClienteAndFechaVencimientoAndEstadoInOrderByIdDesc(
            Long idCliente, LocalDate fechaVencimiento, Collection<EstadoLiquidacion> estados);

    List<Liquidacion> findByEstadoAndFechaVencimientoBefore(EstadoLiquidacion estado, LocalDate fecha);
}
