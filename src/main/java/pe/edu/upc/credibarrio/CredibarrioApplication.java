package pe.edu.upc.credibarrio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** La autenticación es propia (JWT), por eso se excluye el usuario en memoria por defecto de Spring Security. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class CredibarrioApplication {

    public static void main(String[] args) {
        SpringApplication.run(CredibarrioApplication.class, args);
    }
}
