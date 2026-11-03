package com.justjava.ams.systemAdmin.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationHealthDTO {
    private String component;
    private String status;
    private String message;
}
