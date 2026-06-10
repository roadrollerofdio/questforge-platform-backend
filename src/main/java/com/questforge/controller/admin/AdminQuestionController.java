package com.questforge.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.questforge.ai.service.AiQuestionService;
import com.questforge.common.Result;
import com.questforge.dto.AdminDto;
import com.questforge.entity.QuestionBank;
import com.questforge.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/question")
@PreAuthorize("hasRole('ADMIN')")
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
    public Result<Page<QuestionBank>> pageQuestions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String keyword) {

        Page<QuestionBank> page = questionService.pageQuestions(pageNo, pageSize, subjectId, keyword);
        return Result.success(page);
    }

    @PostMapping("/ai-generate")
    public Result<Object> generateByAi(@RequestBody @Valid AdminDto.AiGenerateReq req) {
        String aiGeneratedJson = aiQuestionService.generateQuestionsFromText(req.getDocumentText(), req.getQuestionTypeDesc());
        return Result.success(aiGeneratedJson);
    }
}