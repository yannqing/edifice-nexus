package com.qsy.edifice.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConfigOptionBundleVo {
    private List<ConfigOptionVo> users;
    private List<ConfigOptionVo> roles;
    private List<ConfigOptionVo> positions;
}
