package pe.edu.upc.credibarrio.shared.domain.finance;

import org.junit.jupiter.api.Test;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadPermitida;
import pe.edu.upc.credibarrio.shared.domain.Enums.TipoTasa;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.*;

/** Juegos de datos de prueba del Informe (6.4); mismos casos que financial-math.spec.ts del frontend. */
class FinancialMathTest {

    private static String pct7(double x) {
        return String.format(Locale.ROOT, "%.7f", x * 100);
    }

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    // ---- JuegoDatos1_CompraEnTresCuotasConGraciaTotal
    static final double ted = tasaEfectivaDiaria(new TasaPactada(TipoTasa.EFECTIVA, 0.24, 360, null));
    static final LocalDate fechaPago1 = fechaPagoCiclo(d("2026-09-15"), 20, 26);
    static final CronogramaFrances c = cronogramaFrances(600, ted, d("2026-09-15"), fechaPago1, 3);

    @Test
    void convierteTeaATedYTem() {
        assertThat(pct7(ted)).isEqualTo("0.0597710");
        assertThat(pct7(tasaEfectivaMensual(ted))).isEqualTo("1.8087582");
    }

    @Test
    void capitalizaLosOnceDiasDeGraciaTotal() {
        assertThat(fechaPago1).isEqualTo(d("2026-09-26"));
        assertThat(c.diasGracia()).isEqualTo(11);
        assertThat(c.interesGracia()).isEqualTo(3.96);
        assertThat(c.capitalizado()).isEqualTo(603.96);
        assertThat(String.format(Locale.ROOT, "%.7f", c.factor())).isEqualTo("0.3454638");
        assertThat(c.cuota()).isEqualTo(208.65);
    }

    @Test
    void generaElCronogramaConAjusteDeCierre() {
        assertThat(c.filas()).containsExactly(
                new FilaCronograma(1, d("2026-10-26"), 603.96, 10.92, 197.73, 208.65, 406.23),
                new FilaCronograma(2, d("2026-11-26"), 406.23, 7.35, 201.3, 208.65, 204.93),
                new FilaCronograma(3, d("2026-12-26"), 204.93, 3.71, 204.93, 208.64, 0));
        assertThat(c.totalInteres()).isEqualTo(21.98);
        assertThat(c.totalAmortizacion()).isEqualTo(603.96);
        assertThat(c.totalCuotas()).isEqualTo(625.94);
        assertThat(String.format(Locale.ROOT, "%.4f", c.tea() * 100)).isEqualTo("24.0000");
    }

    // ---- JuegoDatos2_PagoFinDeMesConMora
    static final double TED_C = tasaEfectivaDiaria(new TasaPactada(TipoTasa.EFECTIVA, 0.24, 360, null));
    static final double TED_M = tasaEfectivaDiaria(new TasaPactada(TipoTasa.EFECTIVA, 0.12, 360, null));
    static final LiquidacionFinDeMes liq = liquidarFinDeMes(List.of(
            new ItemFinDeMes(2L, null, d("2026-09-12"), 80),
            new ItemFinDeMes(1L, null, d("2026-09-05"), 100)), TED_C, d("2026-09-26"));

    @Test
    void calculaInteresesCompensatoriosPorDiasOrdenadosPorFecha() {
        assertThat(pct7(TED_M)).isEqualTo("0.0314851");
        assertThat(liq.lineas()).extracting(LineaFinDeMes::dias).containsExactly(21, 14);
        assertThat(liq.lineas()).extracting(LineaFinDeMes::interes).containsExactly(1.26, 0.67);
        assertThat(liq.totalExigible()).isEqualTo(181.93);
    }

    @Test
    void aplicaMoraYPrelacionDelPago() {
        ResultadoMora mora = calcularMora(liq.totalExigible(), TED_M, d("2026-09-26"), d("2026-10-01"));
        assertThat(mora.diasMora()).isEqualTo(5);
        assertThat(mora.interesMoratorio()).isEqualTo(0.29);
        assertThat(mora.totalExigible()).isEqualTo(182.22);
        assertThat(imputarPago(182.22, mora.interesMoratorio(), liq.interesTotal(), mora.totalExigible()))
                .isEqualTo(new Imputacion(0.29, 1.93, 180, 182.22));
    }

    @Test
    void pagoPuntualSinMora() {
        ResultadoMora mora = calcularMora(liq.totalExigible(), TED_M, d("2026-09-26"), d("2026-09-26"));
        assertThat(mora.interesMoratorio()).isZero();
        assertThat(mora.totalExigible()).isEqualTo(181.93);
    }

    @Test
    void rechazaPagosParciales() {
        assertThatThrownBy(() -> imputarPago(150, 0.29, 1.93, 182.22))
                .isInstanceOf(DomainException.class).hasMessageContaining("parciales");
    }

    // ---- JuegoDatos3_TasaNominalYCompraPosteriorAlCorte
    static final double TED_N = tasaEfectivaDiaria(new TasaPactada(TipoTasa.NOMINAL, 0.18, 360, 30));

    @Test
    void convierteTnaCapitalizableMensualmente() {
        assertThat(pct7(TED_N)).isEqualTo("0.0496410");
        assertThat(String.format(Locale.ROOT, "%.4f", tasaEfectivaAnual(TED_N) * 100)).isEqualTo("19.5618");
    }

    @Test
    void trasladaAlCicloSiguienteLasComprasPosterioresAlCorte() {
        LocalDate fa = fechaPagoCiclo(d("2026-09-18"), 20, 26);
        LocalDate fb = fechaPagoCiclo(d("2026-09-22"), 20, 26);
        assertThat(fa).isEqualTo(d("2026-09-26"));
        assertThat(fb).isEqualTo(d("2026-10-26"));
        assertThat(dias360(d("2026-09-18"), fa)).isEqualTo(8);
        assertThat(dias360(d("2026-09-22"), fb)).isEqualTo(34);
        assertThat(interesPorDias(250, TED_N, 8)).isEqualTo(0.99);
        assertThat(interesPorDias(120, TED_N, 34)).isEqualTo(2.04);
    }

    @Test
    void incluyeLaCompraDelMismoDiaDeCorte() {
        assertThat(fechaPagoCiclo(d("2026-09-20"), 20, 26)).isEqualTo(d("2026-09-26"));
        assertThat(fechaPagoCiclo(d("2026-12-21"), 20, 26)).isEqualTo(d("2027-01-26"));
    }

    // ---- ValidacionDeReglas
    @Test
    void rechazaCompraQueExcedeElLimite() {
        assertThat(validarCompra(ModalidadPermitida.AMBAS, ModalidadCompra.FIN_MES, null, 3, 90, 70).getFirst())
                .contains("70.00");
    }

    @Test
    void rechazaPlazoMayorAlPermitido() {
        assertThat(validarCompra(ModalidadPermitida.AMBAS, ModalidadCompra.CUOTAS, 4, 3, 50, 70).getFirst())
                .contains("fuera de rango");
    }

    @Test
    void rechazaModalidadNoPermitida() {
        assertThat(validarCompra(ModalidadPermitida.FIN_MES, ModalidadCompra.CUOTAS, 2, 3, 50, 70).getFirst())
                .contains("no habilitada");
    }

    @Test
    void aceptaUnaCompraValida() {
        assertThat(validarCompra(ModalidadPermitida.AMBAS, ModalidadCompra.FIN_MES, null, 3, 50, 70)).isEmpty();
    }

    @Test
    void respetaLaHoraDeCortePactada() {
        java.time.LocalTime corte18 = java.time.LocalTime.of(18, 0);
        // Mismo día de corte: antes de la hora entra al ciclo; desde la hora pasa al mes siguiente
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.of(2026, 9, 20, 17, 59), 20, corte18, 26)).isEqualTo(d("2026-09-26"));
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.of(2026, 9, 20, 18, 0), 20, corte18, 26)).isEqualTo(d("2026-10-26"));
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.of(2026, 9, 20, 18, 1), 20, corte18, 26)).isEqualTo(d("2026-10-26"));
        // Sin hora pactada se toma 23:59:59
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.of(2026, 9, 20, 23, 59), 20, null, 26)).isEqualTo(d("2026-09-26"));
    }

    @Test
    void respetaElInstanteExactoDeCorteConFraccionesDeSegundo() {
        var corte = java.time.LocalTime.parse("18:00:00.500");
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.parse("2026-09-20T18:00:00.499999999"), 20, corte, 26))
                .isEqualTo(d("2026-09-26"));
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.parse("2026-09-20T18:00:00.500"), 20, corte, 26))
                .isEqualTo(d("2026-10-26"));
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.parse("2026-09-20T18:00:00.500000001"), 20, corte, 26))
                .isEqualTo(d("2026-10-26"));
    }

    @Test
    void aplicaElCorteExactoPorDefectoYEnElCambioDeAno() {
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.parse("2026-09-20T23:59:58.999999999"), 20, null, 26))
                .isEqualTo(d("2026-09-26"));
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.parse("2026-09-20T23:59:59"), 20, null, 26))
                .isEqualTo(d("2026-10-26"));
        assertThat(fechaPagoCiclo(java.time.LocalDateTime.parse("2026-12-20T00:00"), 20, java.time.LocalTime.MIDNIGHT, 26))
                .isEqualTo(d("2027-01-26"));
    }
}
