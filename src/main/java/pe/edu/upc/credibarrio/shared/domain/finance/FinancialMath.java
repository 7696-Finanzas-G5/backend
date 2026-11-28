package pe.edu.upc.credibarrio.shared.domain.finance;

import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadPermitida;
import pe.edu.upc.credibarrio.shared.domain.Enums.TipoTasa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Modelo matemático de CrediBarrio (Informe, punto 6.3). Réplica exacta de
 * {@code shared/domain/finance/financial-math.ts} del frontend.
 *
 * <ul>
 *   <li>Año comercial de 360 días y meses de 30 días.</li>
 *   <li>Tasas en tanto por uno, procesadas sin redondeo (precisión doble).</li>
 *   <li>Montos redondeados a 2 decimales; en el cronograma el redondeo se aplica fila por fila.</li>
 * </ul>
 */
public final class FinancialMath {

    private FinancialMath() {
    }

    // ------------------------------------------------------------------ tipos

    /** Tasa pactada. {@code valor} en tanto por uno (24 % = 0.24); períodos en días. */
    public record TasaPactada(TipoTasa tipo, double valor, int periodoDias, Integer capitalizacionDias) {
    }

    public record ItemFinDeMes(Long id, String descripcion, LocalDate fechaCompra, double capital) {
    }

    public record LineaFinDeMes(Long id, String descripcion, LocalDate fechaCompra, double capital, int dias,
                                double interes, double total) {
    }

    public record LiquidacionFinDeMes(LocalDate fechaPago, List<LineaFinDeMes> lineas, double capitalTotal,
                                      double interesTotal, double totalExigible) {
    }

    public record FilaCronograma(int k, LocalDate fecha, double saldoInicial, double interes, double amortizacion,
                                 double cuota, double saldoFinal) {
    }

    public record CronogramaFrances(double ted, double tem, double tea, int diasGracia, double interesGracia,
                                    double capitalizado, double factor, double cuota, LocalDate fechaFinGracia,
                                    List<FilaCronograma> filas, double totalInteres, double totalAmortizacion,
                                    double totalCuotas) {
    }

    public record ResultadoMora(int diasMora, double interesMoratorio, double base, double totalExigible) {
    }

    public record Imputacion(double mora, double compensatorio, double capital, double total) {
    }

    // ------------------------------------------------------------- utilidades

    /** Redondeo comercial a 2 decimales (equivalente al round2 del frontend). */
    public static double round2(double x) {
        return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static BigDecimal money(double x) {
        return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_UP);
    }

    /** Crea una fecha normalizando el desborde de meses y ajustando el día al último día del mes. */
    public static LocalDate makeDate(int y, int m, int d) {
        LocalDate first = LocalDate.of(y, 1, 1).plusMonths(m - 1L);
        return first.withDayOfMonth(Math.min(d, first.lengthOfMonth()));
    }

    public static LocalDate addMonths(LocalDate fecha, int months, int preferredDay) {
        return makeDate(fecha.getYear(), fecha.getMonthValue() + months, preferredDay);
    }

    // -------------------------------------------------------------- fórmulas

    /** 1. Conteo de días base 360/30: d = (A2−A1)·360 + (M2−M1)·30 + [min(D2,30) − min(D1,30)]. */
    public static int dias360(LocalDate f1, LocalDate f2) {
        return (f2.getYear() - f1.getYear()) * 360
                + (f2.getMonthValue() - f1.getMonthValue()) * 30
                + (Math.min(f2.getDayOfMonth(), 30) - Math.min(f1.getDayOfMonth(), 30));
    }

    /** Hora de corte por defecto cuando no se pactó una: fin del día de corte. */
    public static final LocalTime HORA_CORTE_DEFECTO = LocalTime.of(23, 59, 59);

    /**
     * Indica si una compra entra al ciclo del mes en que se hizo: es anterior al día de corte o es del mismo día de
     * corte antes de la hora de corte pactada. La hora exacta inicia el ciclo siguiente.
     */
    public static boolean antesDelCorte(LocalDateTime fechaHoraCompra, int diaCorte, LocalTime horaCorte) {
        int dia = fechaHoraCompra.getDayOfMonth();
        if (dia != diaCorte) return dia < diaCorte;
        LocalTime hora = horaCorte != null ? horaCorte : HORA_CORTE_DEFECTO;
        return fechaHoraCompra.toLocalTime().isBefore(hora);
    }

    /** 2. Fecha de pago según la fecha y hora de corte: compras posteriores al corte pasan al mes siguiente. */
    public static LocalDate fechaPagoCiclo(LocalDateTime fechaHoraCompra, int diaCorte, LocalTime horaCorte, int diaPago) {
        DomainException.ensure(diaPago > diaCorte, "El día de pago debe ser posterior al día de corte.");
        int mes = fechaHoraCompra.getMonthValue();
        int m = antesDelCorte(fechaHoraCompra, diaCorte, horaCorte) ? mes : mes + 1;
        return makeDate(fechaHoraCompra.getYear(), m, diaPago);
    }

    /** 2. Variante por fecha: equivale a considerar la hora de corte por defecto (23:59:59). */
    public static LocalDate fechaPagoCiclo(LocalDate fechaCompra, int diaCorte, int diaPago) {
        return fechaPagoCiclo(fechaCompra.atStartOfDay(), diaCorte, HORA_CORTE_DEFECTO, diaPago);
    }

    /** Fecha de corte del ciclo cuyo pago vence en {@code fechaPago}. */
    public static LocalDate fechaCorteDeCiclo(LocalDate fechaPago, int diaCorte) {
        return makeDate(fechaPago.getYear(), fechaPago.getMonthValue(), diaCorte);
    }

    /** 3. Conversión de la tasa pactada a Tasa Efectiva Diaria (TED). */
    public static double tasaEfectivaDiaria(TasaPactada t) {
        DomainException.ensure(t.valor() >= 0, "La tasa no puede ser negativa.");
        if (t.tipo() == TipoTasa.EFECTIVA) {
            return Math.pow(1 + t.valor(), 1.0 / t.periodoDias()) - 1;
        }
        Integer m = t.capitalizacionDias();
        DomainException.ensure(m != null && m > 0, "La tasa nominal requiere la frecuencia de capitalización.");
        double iSub = t.valor() * ((double) m / t.periodoDias());
        return Math.pow(1 + iSub, 1.0 / m) - 1;
    }

    /** 4. Tasa Efectiva Mensual (30 días). */
    public static double tasaEfectivaMensual(double ted) {
        return Math.pow(1 + ted, 30) - 1;
    }

    /** 10. Tasa Efectiva Anual equivalente (360 días). Sin comisiones ni seguros, TCEA = TEA. */
    public static double tasaEfectivaAnual(double ted) {
        return Math.pow(1 + ted, 360) - 1;
    }

    /** Interés compuesto por {@code dias} días a la TED: VA·[(1+TED)^d − 1], redondeado a 2 decimales. */
    public static double interesPorDias(double capital, double ted, int dias) {
        if (dias <= 0) return 0;
        return round2(capital * (Math.pow(1 + ted, dias) - 1));
    }

    /** 5. Crédito disponible = límite − deuda vigente. */
    public static double creditoDisponible(double limite, double deudaVigente) {
        return round2(Math.max(0, limite - deudaVigente));
    }

    // ------------------------------------------ 6. Modalidad 1: fin de mes

    public static LiquidacionFinDeMes liquidarFinDeMes(List<ItemFinDeMes> items, double tedC, LocalDate fechaPago) {
        List<LineaFinDeMes> lineas = items.stream()
                .sorted(Comparator.comparing(ItemFinDeMes::fechaCompra))
                .map(it -> {
                    int dias = dias360(it.fechaCompra(), fechaPago);
                    double interes = interesPorDias(it.capital(), tedC, dias);
                    return new LineaFinDeMes(it.id(), it.descripcion(), it.fechaCompra(), it.capital(), dias, interes,
                            round2(it.capital() + interes));
                })
                .toList();
        double capitalTotal = round2(lineas.stream().mapToDouble(LineaFinDeMes::capital).sum());
        double interesTotal = round2(lineas.stream().mapToDouble(LineaFinDeMes::interes).sum());
        return new LiquidacionFinDeMes(fechaPago, lineas, capitalTotal, interesTotal, round2(capitalTotal + interesTotal));
    }

    // ------------------- 7. Modalidad 2: método francés vencido ordinario con gracia total

    /**
     * @param capital     VA (precio de lista financiado)
     * @param tedC        tasa efectiva diaria compensatoria
     * @param fechaCompra fecha de la compra
     * @param fechaPago1  primera fecha de pago (fin del período de gracia total)
     * @param n           número de cuotas mensuales
     */
    public static CronogramaFrances cronogramaFrances(double capital, double tedC, LocalDate fechaCompra,
                                                      LocalDate fechaPago1, int n) {
        DomainException.ensure(n >= 1, "El número de cuotas debe ser un entero ≥ 1.");
        double tem = tasaEfectivaMensual(tedC);
        int diasGracia = Math.max(0, dias360(fechaCompra, fechaPago1));
        double interesGracia = interesPorDias(capital, tedC, diasGracia);
        double capitalizado = round2(capital + interesGracia);
        double factor = (tem * Math.pow(1 + tem, n)) / (Math.pow(1 + tem, n) - 1);
        double cuota = round2(capitalizado * factor);
        int diaPago = fechaPago1.getDayOfMonth();

        List<FilaCronograma> filas = new ArrayList<>();
        double saldo = capitalizado;
        for (int k = 1; k <= n; k++) {
            double interes = round2(saldo * tem);
            double amortizacion = round2(cuota - interes);
            double cuotaK = cuota;
            if (k == n) {
                // Ajuste de cierre: A_n = SI_n para que SF_n = 0.00
                amortizacion = saldo;
                cuotaK = round2(amortizacion + interes);
            }
            double saldoFinal = round2(saldo - amortizacion);
            filas.add(new FilaCronograma(k, addMonths(fechaPago1, k, diaPago), saldo, interes, amortizacion, cuotaK, saldoFinal));
            saldo = saldoFinal;
        }

        return new CronogramaFrances(tedC, tem, tasaEfectivaAnual(tedC), diasGracia, interesGracia, capitalizado, factor,
                cuota, fechaPago1, filas,
                round2(filas.stream().mapToDouble(FilaCronograma::interes).sum()),
                round2(filas.stream().mapToDouble(FilaCronograma::amortizacion).sum()),
                round2(filas.stream().mapToDouble(FilaCronograma::cuota).sum()));
    }

    // ------------------------------------------------- 8. Interés moratorio

    public static ResultadoMora calcularMora(double base, double tedM, LocalDate fechaPactada, LocalDate fechaEfectiva) {
        int diasMora = Math.max(0, dias360(fechaPactada, fechaEfectiva));
        double interesMoratorio = interesPorDias(base, tedM, diasMora);
        return new ResultadoMora(diasMora, interesMoratorio, base, round2(base + interesMoratorio));
    }

    // ------------------- 9. Imputación del pago (prelación: mora → interés compensatorio → capital)

    public static Imputacion imputarPago(double pago, double interesMora, double interesCompensatorio, double totalExigible) {
        if (round2(pago) != round2(totalExigible)) {
            throw new DomainException(String.format(Locale.ROOT,
                    "No se permiten pagos parciales: el pago debe ser exactamente %.2f.", totalExigible));
        }
        double mora = round2(Math.min(pago, interesMora));
        double compensatorio = round2(Math.min(pago - mora, interesCompensatorio));
        double capital = round2(pago - mora - compensatorio);
        return new Imputacion(mora, compensatorio, capital, round2(pago));
    }

    // ------------------------------- Validación de compra (condición de aceptación 6.3.5)

    public static List<String> validarCompra(ModalidadPermitida permitida, ModalidadCompra modalidad, Integer n,
                                             int nMax, double monto, double disponible) {
        List<String> errores = new ArrayList<>();
        if (permitida != ModalidadPermitida.AMBAS && !permitida.name().equals(modalidad.name())) {
            errores.add("Modalidad no habilitada para este producto o servicio.");
        }
        if (modalidad == ModalidadCompra.CUOTAS && (n == null || n < 1 || n > nMax)) {
            errores.add("Número de cuotas fuera de rango (1 a " + nMax + ").");
        }
        if (monto <= 0) {
            errores.add("El monto de la compra debe ser mayor a cero.");
        }
        if (round2(monto) > round2(disponible)) {
            errores.add(String.format(Locale.ROOT,
                    "La compra excede el límite de crédito: crédito disponible %.2f.", disponible));
        }
        return errores;
    }

    // ----------------------------------------------------------- etiquetas

    /** Sigla de la tasa (TEA, TNM, ...). */
    public static String siglaTasa(TipoTasa tipo, int periodoDias) {
        String p = switch (periodoDias) {
            case 360 -> "A";
            case 180 -> "S";
            case 90 -> "T";
            case 60 -> "B";
            case 30 -> "M";
            case 15 -> "Q";
            case 1 -> "D";
            default -> "";
        };
        return (tipo == TipoTasa.EFECTIVA ? "TE" : "TN") + p;
    }
}
