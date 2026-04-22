package com.mauricio.propertyapi.repository;

import com.mauricio.propertyapi.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    // Spring Data query derivation — genera SQL automáticamente del nombre del método
    List<Property> findByCityIgnoreCase(String city);

    List<Property> findByCityAndPriceGreaterThanEqual(String city, BigDecimal minPrice);

    List<Property> findByBedroomsGreaterThanEqual(Integer minBedrooms);

    // JPQL custom query
    @Query("SELECT p FROM Property p WHERE p.city = :city AND p.bedrooms >= :minBedrooms AND p.price <= :maxPrice")
    List<Property> search(
            @Param("city") String city,
            @Param("minBedrooms") Integer minBedrooms,
            @Param("maxPrice") BigDecimal maxPrice
    );
}
