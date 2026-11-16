package pe.edu.upc.credibarrio.shared.domain;

/** Recurso inexistente (o fuera del alcance del usuario). Se responde con HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
