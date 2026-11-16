package pe.edu.upc.credibarrio.catalog.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.credibarrio.catalog.application.CatalogoService;
import pe.edu.upc.credibarrio.catalog.domain.model.ProductoServicio;

import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Catálogo")
@PreAuthorize("hasRole('ADMIN_NEGOCIO')")
public class ProductosController {

    public record CambiarEstadoRequest(@NotNull Boolean activo) {
    }

    private final CatalogoService catalogo;

    public ProductosController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    @Operation(summary = "Catálogo de la tienda en sesión")
    public List<ProductoServicio> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return catalogo.deMiTienda(soloActivos);
    }

    @GetMapping("/unidades-medida")
    public List<String> unidadesMedida() {
        return ProductoServicio.UNIDADES_MEDIDA;
    }

    @GetMapping("/{id}")
    public ProductoServicio obtener(@PathVariable Long id) {
        return catalogo.obtenerDeMiTienda(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoServicio registrar(@Valid @RequestBody ProductoServicio.Datos datos) {
        return catalogo.registrar(datos);
    }

    @PutMapping("/{id}")
    public ProductoServicio actualizar(@PathVariable Long id, @Valid @RequestBody ProductoServicio.Datos datos) {
        return catalogo.actualizar(id, datos);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Alta / baja del producto o servicio")
    public ProductoServicio cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest req) {
        return catalogo.cambiarEstado(id, req.activo());
    }
}
