package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateCitizenRequest {
    @NotNull(message = "Vui lòng chọn loại thiên tai.")
    private Long disasterTypeId;
    @NotBlank(message = "Vui lòng chọn mức độ khẩn cấp.")
    private String urgency;
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự.")
    private String address;
    @DecimalMin(value = "-90.0", message = "Vĩ độ phải từ -90 đến 90.")
    @DecimalMax(value = "90.0", message = "Vĩ độ phải từ -90 đến 90.")
    private BigDecimal latitude;
    @DecimalMin(value = "-180.0", message = "Kinh độ phải từ -180 đến 180.")
    @DecimalMax(value = "180.0", message = "Kinh độ phải từ -180 đến 180.")
    private BigDecimal longitude;
    @Size(max = 10000, message = "Mô tả tối đa 10.000 ký tự.")
    private String description;

    public Long getDisasterTypeId() { return disasterTypeId; }
    public void setDisasterTypeId(Long value) { disasterTypeId = value; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String value) { urgency = value; }
    public String getAddress() { return address; }
    public void setAddress(String value) { address = value; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal value) { latitude = value; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal value) { longitude = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
}
