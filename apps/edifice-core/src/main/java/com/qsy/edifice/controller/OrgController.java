package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.entity.SysDepartment;
import com.qsy.edifice.domain.vo.SysDepartmentTreeVo;
import com.qsy.edifice.mapper.SysDepartmentMapper;
import com.qsy.edifice.utils.ResultUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/org")
public class OrgController {

    @Resource
    private SysDepartmentMapper sysDepartmentMapper;

    @GetMapping("/departments/tree")
    public BaseResponse<List<SysDepartmentTreeVo>> departmentTree() {
        List<SysDepartment> departments = sysDepartmentMapper.selectList(new LambdaQueryWrapper<SysDepartment>()
                .orderByAsc(SysDepartment::getSort)
                .orderByAsc(SysDepartment::getDepartmentId));

        Map<Long, SysDepartmentTreeVo> byId = new LinkedHashMap<>();
        for (SysDepartment department : departments) {
            byId.put(department.getDepartmentId(), SysDepartmentTreeVo.objToVo(department));
        }

        List<SysDepartmentTreeVo> roots = new ArrayList<>();
        for (SysDepartmentTreeVo node : byId.values()) {
            Long parentId = node.getParentId();
            SysDepartmentTreeVo parent = parentId == null ? null : byId.get(parentId);
            if (parent == null || parentId == 0L) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sortTree(roots);
        return ResultUtils.success(Code.SUCCESS, roots);
    }

    private void sortTree(List<SysDepartmentTreeVo> nodes) {
        nodes.sort(Comparator
                .comparing((SysDepartmentTreeVo node) -> node.getSort() == null ? 0 : node.getSort())
                .thenComparing(node -> node.getDepartmentId() == null ? 0L : node.getDepartmentId()));
        nodes.forEach(node -> sortTree(node.getChildren()));
    }
}
