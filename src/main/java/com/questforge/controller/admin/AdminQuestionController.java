package com.questforge.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.questforge.ai.service.AiQuestionService;
import com.questforge.common.Result;
import com.questforge.dto.AdminDto;
import com.questforge.entity.ExamQuestion;
import com.questforge.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 API: 题库与 AI 出题管理
 */
@RestController
@RequestMapping("/admin/question")
@PreAuthorize("hasRole('ADMIN')") // 类级别鉴权：必须是管理员角色
@RequiredArgsConstructor
public class AdminQuestionController {

    private final QuestionService questionService;
    private final AiQuestionService aiQuestionService;

    @PostMapping("/add")
    public Result<Long> addQuestion(@RequestBody @Valid AdminDto.QuestionReq req) {
        Long id = questionService.addOrUpdateQuestion(req);
        return Result.success(id);
    }

    @PutMapping("/update")
    public Result<Long> updateQuestion(@RequestBody @Valid AdminDto.QuestionReq req) {
        Long id = questionService.addOrUpdateQuestion(req);
        return Result.success(id);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<Page<ExamQuestion>> pageQuestions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String keyword) {

        Page<ExamQuestion> page = questionService.pageQuestions(pageNo, pageSize, subjectId, keyword);
        return Result.success(page);
    }

    /**
     * 【AI赋能亮点接口】基于上传的文本或规则智能生成试题
     */
    @PostMapping("/ai-generate")
    public Result<Object> generateByAi(@RequestBody @Valid AdminDto.AiGenerateReq req) {
        // 返回格式为包含多道题目的 JSON 字符串
        String aiGeneratedJson = aiQuestionService.generateQuestionsFromText(req.getDocumentText(), req.getQuestionTypeDesc());
        // 直接返回给前端编辑器进行预览，管理员确认无误后再调用 /add 批量入库
        return Result.success(aiGeneratedJson);
    }
}