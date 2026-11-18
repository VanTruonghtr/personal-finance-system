package vn.edu.uit.personalfinance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "savings_goals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    @Column(name = "target_amount", nullable = false)
    @Builder.Default
    private Double targetAmount = 0.0;

    @Column(name = "current_amount", nullable = false)
    @Builder.Default
    private Double currentAmount = 0.0;

    private LocalDate deadline;

    @Builder.Default
    @Column(columnDefinition = "NVARCHAR(50)")
    private String color = "#10b981";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;
}