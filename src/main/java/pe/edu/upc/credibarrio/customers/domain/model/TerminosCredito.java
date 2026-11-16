package pe.edu.upc.credibarrio.customers.domain.model;

import pe.edu.upc.credibarrio.shared.domain.Enums.FinalidadTasa;
import pe.edu.upc.credibarrio.shared.domain.Enums.Moneda;
import pe.edu.upc.credibarrio.shared.domain.Enums.TipoTasa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import static pe.edu.upc.credibarrio.shared.domain.DomainException.ensure;

/** Value object: términos financieros de la línea de crédito (lo que el negocio pacta con el vecino). */
public record TerminosCredito(Moneda moneda, BigDecimal limiteCredito, Integer plazoMaxMeses, Integer diaCorte,
                              LocalTime horaCorte, Integer diaPago, List<Tasa> tasas) {

    public static final BigDecimal LIMITE_MIN = BigDecimal.valueOf(50);
    public static final BigDecimal LIMITE_MAX = BigDecimal.valueOf(50000);
    public static final int PLAZO_MAX = 24;

    public record Tasa(FinalidadTasa finalidad, TipoTasa tipoTasa, BigDecimal valorTasa, Integer periodoTasaDias,
                       Integer capitalizacionDias) {
    }

    /** Valida las invariantes y devuelve los términos normalizados (escalas fijas, tasas ordenadas). */
    public TerminosCredito validar() {
        ensure(moneda != null, "La moneda es obligatoria.");
        ensure(limiteCredito != null && limiteCredito.compareTo(LIMITE_MIN) >= 0 && limiteCredito.compareTo(LIMITE_MAX) <= 0,
                "El límite de crédito debe estar entre " + LIMITE_MIN + " y " + LIMITE_MAX + ".");
        ensure(plazoMaxMeses != null && plazoMaxMeses >= 1 && plazoMaxMeses <= PLAZO_MAX,
                "El plazo máximo debe estar entre 1 y " + PLAZO_MAX + " meses.");
        ensure(diaCorte != null && diaCorte >= 1 && diaCorte <= 28, "El día de corte debe estar entre 1 y 28.");
        ensure(diaPago != null && diaPago >= 1 && diaPago <= 30, "El día de pago debe estar entre 1 y 30.");
        ensure(diaPago > diaCorte, "El día de pago debe ser posterior al día de corte.");
        ensure(tasas != null, "Debe indicar las tasas compensatoria y moratoria.");
        Tasa comp = tasa(FinalidadTasa.COMPENSATORIA);
        Tasa mora = tasa(FinalidadTasa.MORATORIA);
        ensure(comp != null && comp.valorTasa() != null && comp.valorTasa().signum() > 0
                        && comp.valorTasa().compareTo(BigDecimal.ONE) <= 0,
                "La tasa compensatoria debe ser mayor a 0 % y como máximo 100 %.");
        ensure(mora != null && mora.valorTasa() != null && mora.valorTasa().signum() >= 0
                        && mora.valorTasa().compareTo(BigDecimal.ONE) <= 0,
                "La tasa moratoria debe estar entre 0 % y 100 %.");
        ensure(tasas.size() == 2, "Debe indicar exactamente una tasa compensatoria y una moratoria.");
        for (Tasa t : tasas) {
            ensure(t.tipoTasa() != null, "El tipo de tasa es obligatorio.");
            ensure(t.periodoTasaDias() != null && t.periodoTasaDias() > 0, "El período de la tasa es obligatorio.");
            ensure(t.tipoTasa() == TipoTasa.EFECTIVA || (t.capitalizacionDias() != null && t.capitalizacionDias() > 0),
                    "La tasa nominal requiere la frecuencia de capitalización.");
        }
        List<Tasa> normalizadas = tasas.stream()
                .map(t -> new Tasa(t.finalidad(), t.tipoTasa(), t.valorTasa().setScale(12, RoundingMode.HALF_UP),
                        t.periodoTasaDias(), t.tipoTasa() == TipoTasa.EFECTIVA ? null : t.capitalizacionDias()))
                .sorted(Comparator.comparing(Tasa::finalidad))
                .toList();
        return new TerminosCredito(moneda, limiteCredito.setScale(2, RoundingMode.HALF_UP), plazoMaxMeses, diaCorte,
                horaCorte == null ? LocalTime.of(23, 59, 59) : horaCorte, diaPago, normalizadas);
    }

    public Tasa tasa(FinalidadTasa finalidad) {
        return tasas.stream().filter(t -> t.finalidad() == finalidad).findFirst().orElse(null);
    }
}
