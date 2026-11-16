package pe.edu.upc.credibarrio.billing.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "liquidacion_detalle")
public class LiquidacionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_liquidacion_detalle")
    private Long id;

    @Column(nullable = false)
    private Long idCompra;

    @Column(nullable = false)
    private Integer diasFinanciamiento;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal capital;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interesCompensatorio;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalLinea;

    protected LiquidacionDetalle() {
    }

    public LiquidacionDetalle(Long idCompra, int diasFinanciamiento, BigDecimal capital, BigDecimal interesCompensatorio,
                              BigDecimal totalLinea) {
        this.idCompra = idCompra;
        this.diasFinanciamiento = diasFinanciamiento;
        this.capital = capital;
        this.interesCompensatorio = interesCompensatorio;
        this.totalLinea = totalLinea;
    }

    public Long getId() { return id; }
    public Long getIdCompra() { return idCompra; }
    public Integer getDiasFinanciamiento() { return diasFinanciamiento; }
    public BigDecimal getCapital() { return capital; }
    public BigDecimal getInteresCompensatorio() { return interesCompensatorio; }
    public BigDecimal getTotalLinea() { return totalLinea; }
}
