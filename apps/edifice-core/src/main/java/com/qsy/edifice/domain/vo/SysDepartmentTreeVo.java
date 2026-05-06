package com.qsy.edifice.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.qsy.edifice.domain.entity.SysDepartment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysDepartmentTreeVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    private Integer oaDepartmentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private String name;

    private Integer sort;

    private Integer status;

    private List<SysDepartmentTreeVo> children = new ArrayList<>();

    public static SysDepartmentTreeVo objToVo(SysDepartment department) {
        SysDepartmentTreeVo vo = new SysDepartmentTreeVo();
        BeanUtils.copyProperties(department, vo);
        return vo;
    }
}
