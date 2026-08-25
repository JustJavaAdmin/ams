package com.justjava.ams.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchCreateRequest {
	@NotNull(message = "Organization ID is required")
	private Long organizationId;

	@NotBlank(message = "Branch name is required")
	@Size(max = 255, message = "Branch name must not exceed 255 characters")
	private String name;

	@NotBlank(message = "Branch code is required")
	@Size(max = 255, message = "Branch code must not exceed 255 characters")
	private String code;

	@Size(max = 1000, message = "Address must not exceed 1000 characters")
	private String address;

	@Size(max = 255, message = "City must not exceed 255 characters")
	private String city;

	@Size(max = 255, message = "State must not exceed 255 characters")
	private String state;

	@Size(max = 255, message = "Country must not exceed 255 characters")
	private String country;

	@Size(max = 50, message = "Postal code must not exceed 50 characters")
	private String postalCode;

	@Size(max = 50, message = "Phone must not exceed 50 characters")
	private String phone;

	@Email(message = "Email must be valid")
	@Size(max = 255, message = "Email must not exceed 255 characters")
	private String email;

	private Boolean active = true;
}


