package personalfinance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    @Column(nullable = false, columnDefinition = "NVARCHAR(50)")
    private String type; // INCOME / EXPENSE

    @Builder.Default
    @Column(columnDefinition = "NVARCHAR(100)")
    private String icon = "tag";

    @Builder.Default
    @Column(columnDefinition = "NVARCHAR(50)")
    private String color = "#6366f1";

    @Column(name = "budget_limit")
    @Builder.Default
    private Double budgetLimit = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;
}