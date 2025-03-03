package com.hanaro.wouldyouhana.dto.reservation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class ReservationRequestDTO {

    private Long customerId;
    private String branchName;
    private LocalDateTime reservationDate;
    private String bankerName;
}
