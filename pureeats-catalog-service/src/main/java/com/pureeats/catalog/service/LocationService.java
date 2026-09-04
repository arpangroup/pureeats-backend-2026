package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.LocationAdminResponse;
import com.pureeats.catalog.dto.LocationRequest;
import com.pureeats.catalog.dto.LocationResponse;
import com.pureeats.catalog.dto.PopularGeoPlaceResponse;
import com.pureeats.catalog.repository.LocationRepository;
import com.pureeats.catalog.repository.PopularGeoPlaceRepository;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final PopularGeoPlaceRepository popularGeoPlaceRepository;

    @Transactional(readOnly = true)
    public List<LocationResponse> active() {
        return locationRepository.findByIsActiveTrue().stream()
                .map(l -> new LocationResponse(l.getId(), l.getName(), l.getDescription(), Boolean.TRUE.equals(l.getIsPopular())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> search(String query) {
        log.debug("Searching locations matching '{}'", query);
        return locationRepository.findByIsActiveTrueAndNameContainingIgnoreCase(query).stream()
                .map(l -> new LocationResponse(l.getId(), l.getName(), l.getDescription(), Boolean.TRUE.equals(l.getIsPopular())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> popular() {
        return locationRepository.findByIsActiveTrueAndIsPopularTrue().stream()
                .map(l -> new LocationResponse(l.getId(), l.getName(), l.getDescription(), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PopularGeoPlaceResponse> popularGeoPlaces() {
        return popularGeoPlaceRepository.findByIsActiveTrue().stream()
                .map(p -> new PopularGeoPlaceResponse(p.getId(), p.getName(), p.getLatitude(), p.getLongitude()))
                .toList();
    }

    /** Admin listing - every serviceable location, active or not, so a paused one can still be found and re-enabled. */
    @Transactional(readOnly = true)
    public PageResponse<LocationAdminResponse> listPagedForAdmin(Pageable pageable) {
        Page<Location> page = locationRepository.findAll(pageable);
        return PageResponse.of(page.getContent().stream().map(this::toAdminResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public LocationAdminResponse create(LocationRequest request) {
        log.info("Creating serviceable location '{}'", request.name());
        Location location = new Location();
        location.setName(request.name());
        location.setDescription(request.description());
        location.setIsPopular(Boolean.TRUE.equals(request.isPopular()));
        location.setIsActive(request.isActive() == null || request.isActive());
        LocationAdminResponse response = toAdminResponse(locationRepository.save(location));
        log.info("Serviceable location {} created", response.id());
        return response;
    }

    @Transactional
    public LocationAdminResponse update(Long id, LocationRequest request) {
        log.info("Updating serviceable location {}", id);
        Location location = findOrThrow(id);
        location.setName(request.name());
        location.setDescription(request.description());
        if (request.isPopular() != null) {
            location.setIsPopular(request.isPopular());
        }
        if (request.isActive() != null) {
            location.setIsActive(request.isActive());
        }
        return toAdminResponse(locationRepository.save(location));
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting serviceable location {}", id);
        locationRepository.delete(findOrThrow(id));
    }

    private Location findOrThrow(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Serviceable location {} not found", id);
                    return new ResourceNotFoundException("Location not found: " + id);
                });
    }

    private LocationAdminResponse toAdminResponse(Location l) {
        return new LocationAdminResponse(l.getId(), l.getName(), l.getDescription(),
                Boolean.TRUE.equals(l.getIsPopular()), Boolean.TRUE.equals(l.getIsActive()));
    }
}
