package com.mauricio.propertyapi.service;

import com.mauricio.propertyapi.dto.CreatePropertyRequest;
import com.mauricio.propertyapi.dto.PropertyResponse;
import com.mauricio.propertyapi.dto.ValuationResponse;
import com.mauricio.propertyapi.event.PropertyCreatedEvent;
import com.mauricio.propertyapi.exception.ResourceNotFoundException;
import com.mauricio.propertyapi.model.Property;
import com.mauricio.propertyapi.repository.ProjectRepository;
import com.mauricio.propertyapi.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PropertyService {

    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);

    private final PropertyRepository repository;
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PricingService pricingService;

    public PropertyService(PropertyRepository repository,
                           ProjectRepository projectRepository,
                           ApplicationEventPublisher eventPublisher,
                           PricingService pricingService) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
        this.pricingService = pricingService;
    }

    public PropertyResponse create(CreatePropertyRequest request) {
        var property = new Property();
        property.setName(request.name());
        property.setCity(request.city());
        property.setPrice(request.price());
        property.setBedrooms(request.bedrooms());

        var saved = repository.save(property);

        // --- Publicar evento ---
        // Despues de guardar exitosamente, publicamos el evento.
        // Los @EventListener que escuchan PropertyCreatedEvent se ejecutan automaticamente.
        // Con @Async, se ejecutan en OTRO thread → no hacemos esperar al usuario.
        var event = new PropertyCreatedEvent(
                saved.getId(), saved.getName(), saved.getCity(),
                saved.getPrice(), saved.getBedrooms());
        eventPublisher.publishEvent(event);

        log.info("Property '{}' creada y evento publicado — thread: {}",
                saved.getName(), Thread.currentThread());

        return PropertyResponse.from(saved);
    }

    public PropertyResponse findById(UUID id) {
        return repository.findById(id)
                .map(PropertyResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));
    }

    public List<PropertyResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(PropertyResponse::from)
                .toList();
    }

    public List<PropertyResponse> findByCity(String city) {
        return repository.findByCityIgnoreCase(city)
                .stream()
                .map(PropertyResponse::from)
                .toList();
    }

    public PropertyResponse update(UUID id, CreatePropertyRequest request) {
        var property = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));

        property.setName(request.name());
        property.setCity(request.city());
        property.setPrice(request.price());
        property.setBedrooms(request.bedrooms());

        var saved = repository.save(property);
        return PropertyResponse.from(saved);
    }

    // Asigna una Property existente a un Project existente
    public PropertyResponse assignToProject(UUID propertyId, UUID projectId) {
        var property = repository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        property.setProject(project);
        var saved = repository.save(property);
        return PropertyResponse.from(saved);
    }

    // Desasigna una Property de su Project
    public PropertyResponse removeFromProject(UUID propertyId) {
        var property = repository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        property.setProject(null);
        var saved = repository.save(property);
        return PropertyResponse.from(saved);
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Property", id);
        }
        repository.deleteById(id);
    }

    // --- Delega al PricingService (SRP) ---
    // Antes este metodo construia el JSON-RPC request, llamaba a Feign, y parseaba el response.
    // Ahora solo busca la property y delega la comunicacion al PricingService.
    public ValuationResponse getValuation(UUID id) {
        var property = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", id));

        log.info("Requesting valuation for property '{}' in '{}'", property.getName(), property.getCity());

        var valuation = pricingService.getValuation(property.getCity(), property.getBedrooms());

        return new ValuationResponse(
                property.getId(),
                property.getName(),
                property.getCity(),
                property.getBedrooms(),
                property.getPrice(),
                valuation.estimatedValue(),
                valuation.pricePerSqm(),
                valuation.marketTrend()
        );
    }
}
