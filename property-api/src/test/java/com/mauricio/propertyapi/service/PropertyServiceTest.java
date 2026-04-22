package com.mauricio.propertyapi.service;

import com.mauricio.propertyapi.dto.CreatePropertyRequest;
import com.mauricio.propertyapi.dto.PropertyResponse;
import com.mauricio.propertyapi.model.Property;
import com.mauricio.propertyapi.repository.ProjectRepository;
import com.mauricio.propertyapi.repository.PropertyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.mauricio.propertyapi.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// --- @ExtendWith(MockitoExtension.class) ---
// Le dice a JUnit 5 que use Mockito para este test.
// Equivalente en Kotlin: usabas MockK o Mockito con la misma extension.
// SIN esto, @Mock y @InjectMocks no funcionan.
@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    // --- @Mock ---
    // Crea un objeto FALSO que simula PropertyRepository.
    // No toca la BD real. Tu controlas que devuelve cada metodo.
    @Mock
    PropertyRepository repository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    PricingService pricingService;

    // --- @InjectMocks ---
    // Crea una instancia REAL de PropertyService e inyecta los @Mock en su constructor.
    @InjectMocks
    PropertyService service;

    // --- Helper para crear una Property con datos ---
    private Property buildProperty(UUID id, String name, String city, BigDecimal price, int bedrooms) {
        var property = new Property();
        property.setId(id);
        property.setName(name);
        property.setCity(city);
        property.setPrice(price);
        property.setBedrooms(bedrooms);
        return property;
    }

    // --- @Nested ---
    // Agrupa tests relacionados. Mejora la legibilidad del reporte.
    // No cambia la ejecucion, es puramente organizacional.
    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create a property and return response")
        void shouldCreateProperty() {
            // ARRANGE — preparar datos y definir comportamiento del mock
            var request = new CreatePropertyRequest("Casa Centro", "Lima", BigDecimal.valueOf(200000), 3);
            var savedId = UUID.randomUUID();
            var savedProperty = buildProperty(savedId, "Casa Centro", "Lima", BigDecimal.valueOf(200000), 3);

            // --- when(...).thenReturn(...) ---
            // Cuando el repository reciba save() con CUALQUIER Property, devuelve savedProperty.
            // any(Property.class) es un "argument matcher" — no importa que objeto exacto reciba.
            when(repository.save(any(Property.class))).thenReturn(savedProperty);

            // ACT — ejecutar el metodo que estamos testeando
            PropertyResponse result = service.create(request);

            // ASSERT — verificar resultados
            // --- assertThat (AssertJ) ---
            // Mas legible que assertEquals de JUnit. Lees de izquierda a derecha:
            // "assert that result.name() is equal to Casa Centro"
            assertThat(result.name()).isEqualTo("Casa Centro");
            assertThat(result.city()).isEqualTo("Lima");
            assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(200000));
            assertThat(result.bedrooms()).isEqualTo(3);
            assertThat(result.id()).isEqualTo(savedId);

            // --- verify ---
            // Verifica que repository.save() fue llamado exactamente 1 vez.
            // Si el service no llamara a save(), este test falla.
            verify(repository).save(any(Property.class));

            // Verificamos que el evento fue publicado despues de guardar
            verify(eventPublisher).publishEvent(any(com.mauricio.propertyapi.event.PropertyCreatedEvent.class));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return property when found")
        void shouldReturnPropertyWhenFound() {
            var id = UUID.randomUUID();
            var property = buildProperty(id, "Depto", "Buenos Aires", BigDecimal.valueOf(150000), 2);

            when(repository.findById(id)).thenReturn(Optional.of(property));

            PropertyResponse result = service.findById(id);

            assertThat(result.id()).isEqualTo(id);
            assertThat(result.name()).isEqualTo("Depto");
        }

        @Test
        @DisplayName("should throw 404 when not found")
        void shouldThrow404WhenNotFound() {
            var id = UUID.randomUUID();

            when(repository.findById(id)).thenReturn(Optional.empty());

            // --- assertThatThrownBy ---
            // Verifica que el lambda lanza una excepcion especifica.
            // Mas elegante que try-catch o assertThrows de JUnit.
            assertThatThrownBy(() -> service.findById(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Property not found");
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return all properties")
        void shouldReturnAllProperties() {
            var props = List.of(
                    buildProperty(UUID.randomUUID(), "Casa 1", "Lima", BigDecimal.valueOf(100000), 2),
                    buildProperty(UUID.randomUUID(), "Casa 2", "Bogota", BigDecimal.valueOf(200000), 3)
            );

            when(repository.findAll()).thenReturn(props);

            List<PropertyResponse> result = service.findAll();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("Casa 1");
            assertThat(result.get(1).name()).isEqualTo("Casa 2");
        }

        @Test
        @DisplayName("should return empty list when no properties")
        void shouldReturnEmptyList() {
            when(repository.findAll()).thenReturn(List.of());

            List<PropertyResponse> result = service.findAll();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update property when found")
        void shouldUpdateProperty() {
            var id = UUID.randomUUID();
            var existing = buildProperty(id, "Casa Vieja", "Lima", BigDecimal.valueOf(100000), 2);
            var request = new CreatePropertyRequest("Casa Renovada", "Lima", BigDecimal.valueOf(180000), 3);
            var updated = buildProperty(id, "Casa Renovada", "Lima", BigDecimal.valueOf(180000), 3);

            when(repository.findById(id)).thenReturn(Optional.of(existing));
            when(repository.save(any(Property.class))).thenReturn(updated);

            PropertyResponse result = service.update(id, request);

            assertThat(result.name()).isEqualTo("Casa Renovada");
            assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(180000));
            assertThat(result.bedrooms()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw 404 when updating non-existent property")
        void shouldThrow404WhenUpdatingNonExistent() {
            var id = UUID.randomUUID();
            var request = new CreatePropertyRequest("Casa", "Lima", BigDecimal.valueOf(100000), 2);

            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(id, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Property not found");

            // --- verify never ---
            // Verifica que save() NUNCA fue llamado (no debe guardar si no existe)
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete property when exists")
        void shouldDeleteWhenExists() {
            var id = UUID.randomUUID();

            when(repository.existsById(id)).thenReturn(true);

            service.delete(id);

            verify(repository).deleteById(id);
        }

        @Test
        @DisplayName("should throw 404 when deleting non-existent property")
        void shouldThrow404WhenDeletingNonExistent() {
            var id = UUID.randomUUID();

            when(repository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Property not found");

            verify(repository, never()).deleteById(any());
        }
    }
}
