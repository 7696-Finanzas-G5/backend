package pe.edu.upc.credibarrio.customers.domain.model;

import jakarta.persistence.*;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.FinalidadTasa;
import pe.edu.upc.credibarrio.shared.domain.Enums.Moneda;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Entidad versionada: al cambiar los términos la versión vigente pasa a histórica. */
@Entity
@Table(name = "condicion_credito")
public class CondicionCredito {

    public enum EstadoCondicion { VIGENTE, HISTORICA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condicion")
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Moneda moneda;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteCredito;

    @Column(nullable = false)
    private Integer plazoMaxMeses;

    @Column(nullable = false)
    private Integer diaCorte;

    @Column(nullable = false)
    private LocalTime horaCorte;

    @Column(nullable = false)
    private Integer diaPago;

    @Column(nullable = false)
    private LocalDate vigenteDesde;

    private LocalDate vigenteHasta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCondicion estado;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_condicion", nullable = false)
    @OrderBy("finalidad")
    private List<TasaCondicion> tasas = new ArrayList<>();

    protected CondicionCredito() {
    }

    public CondicionCredito(Long idCliente, TerminosCredito terminos) {
        TerminosCredito t = terminos.validar();
        this.idCliente = idCliente;
        this.moneda = t.moneda();
        this.limiteCredito = t.limiteCredito();
        this.plazoMaxMeses = t.plazoMaxMeses();
        this.diaCorte = t.diaCorte();
        this.horaCorte = t.horaCorte();
        this.diaPago = t.diaPago();
        this.vigenteDesde = LocalDate.now();
        this.estado = EstadoCondicion.VIGENTE;
        t.tasas().forEach(x -> tasas.add(new TasaCondicion(x)));
    }

    public TerminosCredito terminos() {
        return new TerminosCredito(moneda, limiteCredito, plazoMaxMeses, diaCorte, horaCorte, diaPago,
                tasas.stream().map(TasaCondicion::toTermino).sorted(Comparator.comparing(TerminosCredito.Tasa::finalidad)).toList());
    }

    /** Compara con otros términos ya validados/normalizados. */
    public boolean mismosTerminos(TerminosCredito otros) {
        return terminos().validar().equals(otros.validar());
    }

    public void pasarAHistorica() {
        this.estado = EstadoCondicion.HISTORICA;
        this.vigenteHasta = LocalDate.now();
    }

    /** Fija la fecha de inicio de vigencia (usado por la carga de datos de demostración). */
    public void vigenteDesde(LocalDate fecha) {
        this.vigenteDesde = fecha;
    }

    public TasaCondicion tasa(FinalidadTasa finalidad) {
        return tasas.stream().filter(t -> t.getFinalidad() == finalidad).findFirst()
                .orElseThrow(() -> new DomainException("La condición de crédito no tiene tasa "
                        + finalidad.name().toLowerCase(Locale.ROOT) + "."));
    }

    public double tedCompensatoria() {
        return FinancialMath.tasaEfectivaDiaria(tasa(FinalidadTasa.COMPENSATORIA).toTasaPactada());
    }

    public double tedMoratoria() {
        return FinancialMath.tasaEfectivaDiaria(tasa(FinalidadTasa.MORATORIA).toTasaPactada());
    }

    /** "TEA 24.0000%" */
    public String tasaLabel(FinalidadTasa finalidad) {
        TasaCondicion t = tasa(finalidad);
        return FinancialMath.siglaTasa(t.getTipoTasa(), t.getPeriodoTasaDias()) + " "
                + String.format(Locale.ROOT, "%.4f%%", t.getValorTasa().doubleValue() * 100);
    }

    public Long getId() { return id; }
    public Long getIdCliente() { return idCliente; }
    public Moneda getMoneda() { return moneda; }
    public BigDecimal getLimiteCredito() { return limiteCredito; }
    public Integer getPlazoMaxMeses() { return plazoMaxMeses; }
    public Integer getDiaCorte() { return diaCorte; }
    public LocalTime getHoraCorte() { return horaCorte; }
    public Integer getDiaPago() { return diaPago; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public EstadoCondicion getEstado() { return estado; }
    public List<TasaCondicion> getTasas() { return tasas; }
}
