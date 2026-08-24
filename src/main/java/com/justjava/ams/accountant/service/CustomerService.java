package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.CustomerDTO;
import com.justjava.ams.accountant.entity.Customer;
import com.justjava.ams.accountant.repository.CustomerRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final OrganizationRepository organizationRepository;

    public CustomerDTO createCustomer(Long organizationId, CustomerDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        String code = required(dto.getCustomerCode(), "Customer code is required");
        if (customerRepository.existsByOrganizationIdAndCustomerCodeIgnoreCase(organizationId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer code already exists");
        }
        Customer customer = Customer.builder()
                .organization(organization)
                .customerCode(code)
                .legalName(required(dto.getLegalName(), "Customer legal name is required"))
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .billingAddress(dto.getBillingAddress())
                .taxId(dto.getTaxId())
                .paymentTerms(dto.getPaymentTerms())
                .creditLimit(dto.getCreditLimit() != null ? dto.getCreditLimit() : BigDecimal.ZERO)
                .creditHold(dto.getCreditHold() != null ? dto.getCreditHold() : false)
                .creditHoldReason(dto.getCreditHoldReason())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();
        return mapToDTO(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerDTO> getActiveCustomers(Long organizationId) {
        return customerRepository.findByOrganizationIdAndActiveTrueOrderByLegalNameAsc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private CustomerDTO mapToDTO(Customer customer) {
        return CustomerDTO.builder()
                .id(customer.getId())
                .organizationId(customer.getOrganization().getId())
                .customerCode(customer.getCustomerCode())
                .legalName(customer.getLegalName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .billingAddress(customer.getBillingAddress())
                .taxId(customer.getTaxId())
                .paymentTerms(customer.getPaymentTerms())
                .creditLimit(customer.getCreditLimit())
                .creditHold(customer.getCreditHold())
                .creditHoldReason(customer.getCreditHoldReason())
                .creditHoldPlacedBy(customer.getCreditHoldPlacedBy())
                .creditHoldPlacedAt(customer.getCreditHoldPlacedAt())
                .creditHoldReleasedBy(customer.getCreditHoldReleasedBy())
                .creditHoldReleasedAt(customer.getCreditHoldReleasedAt())
                .active(customer.getActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
