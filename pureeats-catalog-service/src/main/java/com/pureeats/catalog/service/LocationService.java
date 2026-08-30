package com.pureeats.catalog.service;

import com.pureeats.catalog.dto.LocationResponse;
import com.pureeats.catalog.dto.PopularGeoPlaceResponse;
import com.pureeats.catalog.repository.LocationRepository;
import com.pureeats.catalog.repository.PopularGeoPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final PopularGeoPlaceRepository popularGeoPlaceRepository;

    @Transactional(readOnly = true)
    public List<LocationResponse> search(String query) {
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
}
