package com.questforge.controller.admin;

import com.questforge.common.Result;
import com.questforge.dto.AdminDto;
import com.questforge.service.SysConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SysConfigService sysConfigService;

    @GetMapping
    public Result<AdminDto.SystemSettingsResp> getSettings() {
        return Result.success(sysConfigService.getSettings());
    }

    @PutMapping
    public Result<Void> saveSettings(@RequestBody @Valid AdminDto.SystemSettingsReq req) {
        sysConfigService.saveSettings(req);
        return Result.success(null, "系统配置保存成功");
    }
}
