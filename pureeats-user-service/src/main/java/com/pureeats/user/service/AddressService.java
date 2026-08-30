package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ForbiddenException;
import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.Address;
import com.pureeats.domain.entity.User;
import com.pureeats.user.dto.AddressRequest;
import com.pureeats.user.dto.AddressResponse;
import com.pureeats.user.repository.AddressRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        User user = findUser(userId);
        return addressRepository.findByUserId(userId.intValue()).stream()
                .map(a -> toResponse(a, user))
                .toList();
    }

    @Transactional
    public AddressResponse save(Long userId, AddressRequest request) {
        User user = findUser(userId);

        Address address = new Address();
        address.setUserId(userId.intValue());
        address.setHouse(request.house());
        address.setAddress(request.address());
        address.setLandmark(request.landmark());
        address.setTag(request.tag());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        address = addressRepository.save(address);

        boolean isFirstAddress = user.getDefaultAddressId() == null;
        if (isFirstAddress || request.makeDefault()) {
            user.setDefaultAddressId(address.getId().intValue());
            userRepository.save(user);
        }
        return toResponse(address, user);
    }

    @Transactional
    public AddressResponse edit(Long userId, Long addressId, AddressRequest request) {
        Address address = findOwnedAddress(userId, addressId);
        address.setHouse(request.house());
        address.setAddress(request.address());
        address.setLandmark(request.landmark());
        address.setTag(request.tag());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        address.setUpdatedAt(LocalDateTime.now());
        addressRepository.save(address);

        User user = findUser(userId);
        if (request.makeDefault()) {
            user.setDefaultAddressId(address.getId().intValue());
            userRepository.save(user);
        }
        return toResponse(address, user);
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = findOwnedAddress(userId, addressId);
        addressRepository.delete(address);

        User user = findUser(userId);
        if (address.getId().intValue() == (user.getDefaultAddressId() == null ? -1 : user.getDefaultAddressId())) {
            user.setDefaultAddressId(null);
            userRepository.save(user);
        }
    }

    @Transactional
    public void setDefault(Long userId, Long addressId) {
        Address address = findOwnedAddress(userId, addressId);
        User user = findUser(userId);
        user.setDefaultAddressId(address.getId().intValue());
        userRepository.save(user);
    }

    private Address findOwnedAddress(Long userId, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + addressId));
        if (!address.getUserId().equals(userId.intValue())) {
            throw new ForbiddenException("This address does not belong to you");
        }
        return address;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private AddressResponse toResponse(Address address, User user) {
        boolean isDefault = user.getDefaultAddressId() != null
                && user.getDefaultAddressId().longValue() == address.getId();
        return new AddressResponse(
                address.getId(), address.getHouse(), address.getAddress(), address.getLandmark(),
                address.getTag(), address.getLatitude(), address.getLongitude(), isDefault);
    }
}
