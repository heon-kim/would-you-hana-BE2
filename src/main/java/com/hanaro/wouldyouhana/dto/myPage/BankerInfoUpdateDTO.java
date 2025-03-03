package com.hanaro.wouldyouhana.dto.myPage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class BankerInfoUpdateDTO {

    private String password;
    private String branchName;
}
