package com.questforge.controller.admin;

import com.questforge.common.Result;
import com.questforge.dto.AdminDto;
import com.questforge.service.SysSubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端 API：
 */
@RestController
@RequestMapping("/admin/subject")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final SysSubjectService sysSubjectService;

    @PostMapping("/save")
    public Result<Void> saveSubject(@RequestBody @Valid AdminDto.SubjectReq req) {
        sysSubjectService.saveOrUpdateSubject(req);
        return Result.success(null, "知识域分类保存成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSubject(@PathVariable Long id) {
        sysSubjectService.deleteSubject(id);
        return Result.success(null, "已成功移除该节点");
    }

    @GetMapping("/tree")
    public Result<List<AdminDto.SubjectTreeResp>> getSubjectTree() {
        return Result.success(sysSubjectService.getSubjectTree());
    }
}