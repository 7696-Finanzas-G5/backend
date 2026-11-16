package pe.edu.upc.credibarrio.shared.domain;

/** Tipos compartidos entre bounded contexts (shared kernel). */
public final class Enums {

    private Enums() {
    }

    public enum EstadoActivo { ACTIVO, INACTIVO }

    public enum Moneda { PEN, USD }

    public enum TipoTasa { NOMINAL, EFECTIVA }

    public enum FinalidadTasa { COMPENSATORIA, MORATORIA }

    public enum ModalidadPermitida { FIN_MES, CUOTAS, AMBAS }

    public enum ModalidadCompra { FIN_MES, CUOTAS }

    public enum Rol { ADMIN_SISTEMA, ADMIN_NEGOCIO, CLIENTE }
}
