package com.qsy.edifice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.qsy.edifice.common.Code;
import com.qsy.edifice.domain.common.BaseResponse;
import com.qsy.edifice.domain.dto.CreateAnnouncementDto;
import com.qsy.edifice.domain.dto.GetAnnouncementListDto;
import com.qsy.edifice.domain.dto.UpdateAnnouncementDto;
import com.qsy.edifice.domain.entity.SysUser;
import com.qsy.edifice.domain.vo.AnnouncementVo;
import com.qsy.edifice.service.AnnouncementService;
import com.qsy.edifice.utils.JwtUtils;
import com.qsy.edifice.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "公告管理")
@RestController
@RequestMapping("/announcement")
public class AnnouncementController {

    @Resource
    private AnnouncementService announcementService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/list")
    @Operation(summary = "公告列表", description = "管理端：分页 + 关键字 / 状态 / 优先级筛选")
    public BaseResponse<Page<AnnouncementVo>> getList(GetAnnouncementListDto dto) {
        Page<AnnouncementVo> result = announcementService.getAnnouncementList(dto);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/recent")
    @Operation(summary = "首页公告", description = "返回最近已发布且未过期的公告（默认 5 条）")
    public BaseResponse<List<AnnouncementVo>> getRecent(
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<AnnouncementVo> result = announcementService.getRecentPublished(limit);
        return ResultUtils.success(Code.SUCCESS, result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "公告详情")
    public BaseResponse<AnnouncementVo> getById(@PathVariable("id") Long id) {
        AnnouncementVo vo = announcementService.getAnnouncementById(id);
        return ResultUtils.success(Code.SUCCESS, vo);
    }

    @PostMapping("/create")
    @Operation(summary = "创建公告", description = "status=1 时立即发布；否则保存为草稿")
    public BaseResponse<Long> create(@RequestBody CreateAnnouncementDto dto,
                                     HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        Long id = announcementService.createAnnouncement(dto, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, id, "创建成功");
    }

    @PutMapping("/update")
    @Operation(summary = "更新公告")
    public BaseResponse<Boolean> update(@RequestBody UpdateAnnouncementDto dto) {
        announcementService.updateAnnouncement(dto);
        return ResultUtils.success(Code.SUCCESS, true, "更新成功");
    }

    @PutMapping("/publish/{id}")
    @Operation(summary = "发布公告", description = "草稿或已下线 → 已发布")
    public BaseResponse<Boolean> publish(@PathVariable("id") Long id,
                                         HttpServletRequest request) throws JsonProcessingException {
        String token = request.getHeader("token");
        SysUser loginUser = jwtUtils.getUserFromToken(token);
        announcementService.publishAnnouncement(id, loginUser.getUserId());
        return ResultUtils.success(Code.SUCCESS, true, "发布成功");
    }

    @PutMapping("/unpublish/{id}")
    @Operation(summary = "下线公告", description = "已发布 → 已下线")
    public BaseResponse<Boolean> unpublish(@PathVariable("id") Long id) {
        announcementService.unpublishAnnouncement(id);
        return ResultUtils.success(Code.SUCCESS, true, "下线成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告")
    public BaseResponse<Boolean> delete(@PathVariable("id") Long id) {
        announcementService.deleteAnnouncement(id);
        return ResultUtils.success(Code.SUCCESS, true, "删除成功");
    }
}
