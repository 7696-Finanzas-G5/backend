package pe.edu.upc.credibarrio.dashboard.interfaces.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upc.credibarrio.dashboard.application.DashboardService;
import pe.edu.upc.credibarrio.dashboard.application.DashboardService.ResumenNegocio;
import pe.edu.upc.credibarrio.dashboard.application.DashboardService.ResumenSistema;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Inicio")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/sistema")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResumenSistema sistema() {
        return dashboard.resumenSistema();
    }

    @GetMapping("/negocio")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResumenNegocio negocio() {
        return dashboard.resumenNegocio();
    }
}
