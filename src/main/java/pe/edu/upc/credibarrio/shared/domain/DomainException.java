package pe.edu.upc.credibarrio.shared.domain;

/** Violación de una regla de negocio (invariante del dominio). Se responde con HTTP 422. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public static void ensure(boolean condition, String message) {
        if (!condition) throw new DomainException(message);
    }
}
