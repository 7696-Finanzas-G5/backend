package pe.edu.upc.credibarrio.shared.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI credibarrioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CrediBarrio API")
                        .version("v1")
                        .description("Ventas al crédito para negocios de barrio: pago a fin de mes y en cuotas "
                                + "(método francés vencido ordinario con gracia total), mora e imputación de pagos."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
