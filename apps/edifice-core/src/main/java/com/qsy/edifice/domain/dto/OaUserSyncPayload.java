package com.qsy.edifice.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OaUserSyncPayload {

    private String event;

    private Long userId;

    private String username;

    private String realName;

    private String email;

    private String phone;

    private Long deptId;

    private Integer status;

    private String password;
}
