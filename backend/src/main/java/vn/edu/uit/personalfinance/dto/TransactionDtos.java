package vn.edu.uit.personalfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionDtos {
    @Data
    public static class TransactionRequest {
        @NotNull(message = "Vui lòng chọn ví nguồn")
        private Long walletId;

        private Long targetWalletId; // Bắt buộc nếu type = TRANSFER
        private Long categoryId;     // Bắt buộc nếu type = INCOME hoặc EXPENSE

        @NotBlank(message = "Loại giao dịch không được để trống")
        private String type;         // INCOME, EXPENSE, TRANSFER

        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
        private BigDecimal amount;

        private LocalDateTime transactionDate;
        private String description;
        private String note;
    }

    public record TransactionResponse(
            Long id,
            String type,
            BigDecimal amount,
            LocalDateTime transactionDate,
            String description,
            String note,
            Long walletId,
            String walletName,
            Long targetWalletId,
            String targetWalletName,
            Long categoryId,
            String categoryName,
            String categoryColor
    ) {}
}