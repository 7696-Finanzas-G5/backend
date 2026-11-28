package pe.edu.upc.credibarrio.billing.infrastructure.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService.ResultadoCorte;

/**
 * Genera automáticamente, en la fecha de corte, el listado de pago de cada cliente (enunciado, punto 6).
 *
 * <p>Comprueba los cortes en los segundos 0 y 59 de cada minuto y al iniciar la aplicación. Así el corte por
 * defecto (23:59:59) se procesa ese mismo día, sin consultar la base de datos cada segundo. Las demás horas
 * se procesan en la siguiente comprobación, con una demora inferior a un minuto si el servidor está activo.
 * Si el servidor estuvo apagado o
 * dormido en el momento del corte (hosting gratuito), el listado se genera en la siguiente ejecución. El proceso es
 * idempotente, así que repetirlo no duplica listados.</p>
 */
@Component
public class CorteScheduler {

    private static final Logger log = LoggerFactory.getLogger(CorteScheduler.class);

    private final EstadoCuentaService estadoCuenta;

    public CorteScheduler(EstadoCuentaService estadoCuenta) {
        this.estadoCuenta = estadoCuenta;
    }

    @Scheduled(cron = "0,59 * * * * *", zone = "America/Lima")
    public void cadaMinuto() {
        ejecutar();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        ejecutar();
    }

    private void ejecutar() {
        try {
            ResultadoCorte r = estadoCuenta.ejecutarCorte();
            if (r.listadosGenerados() > 0 || r.listadosVencidos() > 0) {
                log.info("Corte automático: {} listado(s) generado(s), {} vencido(s).", r.listadosGenerados(),
                        r.listadosVencidos());
            }
        } catch (RuntimeException e) {
            log.error("Falló el proceso automático de corte", e);
        }
    }
}
