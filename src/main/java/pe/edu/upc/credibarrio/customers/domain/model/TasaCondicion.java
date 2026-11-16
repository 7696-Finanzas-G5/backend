package pe.edu.upc.credibarrio.customers.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.Enums.FinalidadTasa;
import pe.edu.upc.credibarrio.shared.domain.Enums.TipoTasa;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.TasaPactada;

import java.math.BigDecimal;

/** Tasa pactada con el cliente (compensatoria o moratoria). */
@Entity
@Table(name = "tasa_condicion")
public class TasaCondicion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tasa")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private FinalidadTasa finalidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTasa tipoTasa;

    /** Tanto por uno (0.24 = 24 %). */
    @Column(nullable = false, precision = 18, scale = 12)
    private BigDecimal valorTasa;

    @Column(nullable = false)
    private Integer periodoTasaDias;

    private Integer capitalizacionDias;

    protected TasaCondicion() {
    }

    public TasaCondicion(TerminosCredito.Tasa t) {
        this.finalidad = t.finalidad();
        this.tipoTasa = t.tipoTasa();
        this.valorTasa = t.valorTasa();
        this.periodoTasaDias = t.periodoTasaDias();
        this.capitalizacionDias = t.capitalizacionDias();
    }

    public TasaPactada toTasaPactada() {
        return new TasaPactada(tipoTasa, valorTasa.doubleValue(), periodoTasaDias, capitalizacionDias);
    }

    public TerminosCredito.Tasa toTermino() {
        return new TerminosCredito.Tasa(finalidad, tipoTasa, valorTasa, periodoTasaDias, capitalizacionDias);
    }

    public Long getId() { return id; }
    public FinalidadTasa getFinalidad() { return finalidad; }
    public TipoTasa getTipoTasa() { return tipoTasa; }
    public BigDecimal getValorTasa() { return valorTasa; }
    public Integer getPeriodoTasaDias() { return periodoTasaDias; }
    public Integer getCapitalizacionDias() { return capitalizacionDias; }
}
