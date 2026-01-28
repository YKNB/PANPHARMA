package com.pharmaops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateBatchRequest {

    @NotBlank
    @Size(max = 64)
    private String batchNumber;

    @NotBlank
    @Size(max = 64)
    private String productCode;

    // getters/setters
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
}
