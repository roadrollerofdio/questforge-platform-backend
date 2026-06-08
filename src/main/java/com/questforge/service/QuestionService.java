package com.questforge.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.questforge.dto.AdminDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 题库管理业务实现
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final ExamQuestionMapper examQuestionMapper;

    public Long addOrUpdateQuestion(AdminDto.QuestionReq req) {
        ExamQuestion question = BeanUtil.copyProperties(req, ExamQuestion.class);
        if (question.getId() == null) {
            examQuestionMapper.insert(question);
        } else {
            examQuestionMapper.updateById(question);
        }
        return question.getId();
    }

    public void deleteQuestion(Long id) {
        examQuestionMapper.deleteById(id);
    }

    public Page<ExamQuestion> pageQuestions(int pageNo, int pageSize, Long subjectId, String keyword) {
        LambdaQueryWrapper<ExamQuestion> wrapper = new LambdaQueryWrapper<>();
        if (subjectId != null) {
            wrapper.eq(ExamQuestion::getSubjectId, subjectId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ExamQuestion::getContent, keyword);
        }
        wrapper.orderByDesc(ExamQuestion::getCreateTime);
        return examQuestionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }
}