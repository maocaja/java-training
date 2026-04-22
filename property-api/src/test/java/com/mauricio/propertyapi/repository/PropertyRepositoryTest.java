package com.mauricio.propertyapi.repository;

import com.mauricio.propertyapi.model.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// --- @DataJpaTest ---
// Levanta SOLO la capa de JPA (no controllers, no services, no web).
// Configura automaticamente una BD en memoria (H2).
// Cada test se ejecuta en una transaccion que hace ROLLBACK al final.
// Es mas rapido que @SpringBootTest porque carga menos contexto.
//
// Pregunta de entrevista: "Diferencia entre @SpringBootTest y @DataJpaTest?"
// → @SpringBootTest carga TODO. @DataJpaTest carga solo JPA + BD.
//   Usa @DataJpaTest para testear queries, @SpringBootTest para integracion completa.
@DataJpaTest
class PropertyRepositoryTest {

    @Autowired
    PropertyRepository repository;

    // --- @BeforeEach ---
    // Se ejecuta ANTES de cada test. Prepara datos comunes.
    // Cada test empieza con la misma BD porque @DataJpaTest hace rollback.
    @BeforeEach
    void setUp() {
        repository.save(buildProperty("Casa Lima", "Lima", BigDecimal.valueOf(150000), 3));
        repository.save(buildProperty("Depto Lima", "Lima", BigDecimal.valueOf(80000), 2));
        repository.save(buildProperty("Casa Bogota", "Bogota", BigDecimal.valueOf(200000), 4));
        repository.save(buildProperty("Penthouse Bogota", "Bogota", BigDecimal.valueOf(500000), 5));
    }

    private Property buildProperty(String name, String city, BigDecimal price, int bedrooms) {
        var property = new Property();
        property.setName(name);
        property.setCity(city);
        property.setPrice(price);
        property.setBedrooms(bedrooms);
        return property;
    }

    @Test
    @DisplayName("findByCityIgnoreCase should find properties by city (case insensitive)")
    void shouldFindByCityIgnoreCase() {
        // "lima" en minuscula debe encontrar "Lima"
        var results = repository.findByCityIgnoreCase("lima");

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(p ->
                assertThat(p.getCity()).isEqualToIgnoringCase("Lima"));
    }

    @Test
    @DisplayName("findByCityIgnoreCase should return empty for non-existent city")
    void shouldReturnEmptyForNonExistentCity() {
        var results = repository.findByCityIgnoreCase("Santiago");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findByCityAndPriceGreaterThanEqual should filter by city and min price")
    void shouldFilterByCityAndMinPrice() {
        // Bogota con precio >= 300000 → solo Penthouse (500000)
        var results = repository.findByCityAndPriceGreaterThanEqual(
                "Bogota", BigDecimal.valueOf(300000));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Penthouse Bogota");
    }

    @Test
    @DisplayName("findByBedroomsGreaterThanEqual should filter by min bedrooms")
    void shouldFilterByMinBedrooms() {
        // >= 4 bedrooms → Casa Bogota (4) + Penthouse Bogota (5)
        var results = repository.findByBedroomsGreaterThanEqual(4);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(p ->
                assertThat(p.getBedrooms()).isGreaterThanOrEqualTo(4));
    }

    @Test
    @DisplayName("search (JPQL) should filter by city, min bedrooms, and max price")
    void shouldSearchWithCustomQuery() {
        // Bogota, >= 3 bedrooms, <= 300000 → solo Casa Bogota (4 bed, 200000)
        var results = repository.search("Bogota", 3, BigDecimal.valueOf(300000));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Casa Bogota");
    }

    @Test
    @DisplayName("search should return empty when no match")
    void shouldReturnEmptyWhenNoSearchMatch() {
        // Lima, >= 5 bedrooms → no hay ninguna
        var results = repository.search("Lima", 5, BigDecimal.valueOf(1000000));

        assertThat(results).isEmpty();
    }
}
