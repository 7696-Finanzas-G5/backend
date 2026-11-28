package pe.edu.upc.credibarrio.credits.domain.model;

import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadPermitida;
import pe.edu.upc.credibarrio.shared.domain.Enums.Moneda;

import java.time.LocalTime;

/**
 * Vista que el contexto de Créditos necesita de la línea de crédito del cliente.
 * Se construye desde el contexto Customers mediante la capa anticorrupción.
 */
public record CondicionesFinancieras(Long idCondicion, Moneda moneda, double limiteCredito, int plazoMaxMeses,
                                     int diaCorte, int diaPago, double tedCompensatoria, double tedMoratoria,
                                     String tasaCompensatoriaLabel, String tasaMoratoriaLabel, LocalTime horaCorte) {

    /** Vista del producto que se ofrece al crédito (traducida desde el contexto Catalog). */
    public record ProductoOfertado(Long id, Long idTienda, String descripcion, double precioCredito,
                                   ModalidadPermitida modalidadPermitida, boolean activo) {
    }
}
