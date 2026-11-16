package pe.edu.upc.credibarrio.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.catalog.domain.model.ProductoServicio;
import pe.edu.upc.credibarrio.catalog.domain.model.ProductoServicio.TipoItem;
import pe.edu.upc.credibarrio.catalog.domain.repositories.ProductoRepository;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.shared.domain.NotFoundException;

import java.util.List;

/** Casos de uso del Administrador del Negocio: alta y baja de productos y servicios. */
@Service
@Transactional
public class CatalogoService {

    private final ProductoRepository repo;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public CatalogoService(ProductoRepository repo, CurrentUser currentUser, AuditService audit) {
        this.repo = repo;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** Catálogo de la tienda del usuario en sesión. */
    @Transactional(readOnly = true)
    public List<ProductoServicio> deMiTienda(boolean soloActivos) {
        Long idTienda = currentUser.tiendaId();
        return soloActivos
                ? repo.findByIdTiendaAndEstadoOrderById(idTienda, EstadoActivo.ACTIVO)
                : repo.findByIdTiendaOrderById(idTienda);
    }

    /** Producto de la tienda en sesión (404 si pertenece a otra tienda). */
    @Transactional(readOnly = true)
    public ProductoServicio obtenerDeMiTienda(Long id) {
        Long idTienda = currentUser.tiendaId();
        return repo.findById(id).filter(p -> p.getIdTienda().equals(idTienda))
                .orElseThrow(() -> new NotFoundException("Producto no encontrado."));
    }

    @Transactional(readOnly = true)
    public ProductoServicio obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Producto no encontrado."));
    }

    public ProductoServicio registrar(ProductoServicio.Datos datos) {
        Long idTienda = currentUser.tiendaId();
        TipoItem tipo = datos.tipoItem() == null ? TipoItem.PRODUCTO : datos.tipoItem();
        long n = repo.countByIdTiendaAndTipoItem(idTienda, tipo) + 1;
        String codigo = (tipo == TipoItem.SERVICIO ? "S" : "P") + "-" + String.format("%03d", n);
        ProductoServicio p = repo.save(new ProductoServicio(idTienda, codigo, datos, true));
        audit.registrar("ALTA", "producto_servicio", p.getId(), p.getDescripcion());
        return p;
    }

    public ProductoServicio actualizar(Long id, ProductoServicio.Datos datos) {
        ProductoServicio p = obtenerDeMiTienda(id);
        // Se exige imagen si el producto ya la tenía (los datos de demostración no tienen imagen)
        p.actualizar(datos, p.getImagenUrl() != null);
        audit.registrar("EDITAR", "producto_servicio", id, p.getDescripcion());
        return p;
    }

    public ProductoServicio cambiarEstado(Long id, boolean activo) {
        ProductoServicio p = obtenerDeMiTienda(id);
        p.cambiarEstado(activo ? EstadoActivo.ACTIVO : EstadoActivo.INACTIVO);
        audit.registrar(activo ? "ALTA" : "BAJA", "producto_servicio", id, p.getDescripcion());
        return p;
    }
}
