package pe.edu.upc.credibarrio.billing.infrastructure.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorteSchedulerTest {

    @Test
    void recuperaLosCortesPendientesAlIniciar() {
        EstadoCuentaService estadoCuenta = mock(EstadoCuentaService.class);
        when(estadoCuenta.ejecutarCorte()).thenReturn(new EstadoCuentaService.ResultadoCorte(
                LocalDateTime.of(2026, 9, 21, 8, 0), 1, 0));

        new CorteScheduler(estadoCuenta).alIniciar();

        verify(estadoCuenta).ejecutarCorte();
    }

    @Test
    void compruebaElCortePorDefectoAntesDeQueTermineElDia() throws NoSuchMethodException {
        Scheduled schedule = CorteScheduler.class.getMethod("cadaMinuto").getAnnotation(Scheduled.class);
        CronExpression cron = CronExpression.parse(schedule.cron());

        assertThat(schedule.zone()).isEqualTo("America/Lima");
        assertThat(cron.next(LocalDateTime.of(2026, 9, 20, 23, 59, 58)))
                .isEqualTo(LocalDateTime.of(2026, 9, 20, 23, 59, 59));
        assertThat(cron.next(LocalDateTime.of(2026, 9, 20, 18, 0)))
                .isEqualTo(LocalDateTime.of(2026, 9, 20, 18, 0, 59));
    }
}
