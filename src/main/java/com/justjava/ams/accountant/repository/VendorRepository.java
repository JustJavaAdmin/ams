package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    List<Vendor> findByOrganizationIdAndActiveTrueOrderByLegalNameAsc(Long organizationId);
    Optional<Vendor> findByOrganizationIdAndVendorCodeIgnoreCase(Long organizationId, String vendorCode);
    boolean existsByOrganizationIdAndVendorCodeIgnoreCase(Long organizationId, String vendorCode);
}
