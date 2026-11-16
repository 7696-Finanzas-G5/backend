package pe.edu.upc.credibarrio.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.audit.application.AuditService.RegistroView;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bitacora")
@Tag(name = "Auditoría")
public class BitacoraController {

    private final AuditService audit;

    public BitacoraController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA','ADMIN_NEGOCIO')")
    public List<RegistroView> recientes(@RequestParam(defaultValue = "50") int limite) {
        return audit.recientes(limite);
    }
}
