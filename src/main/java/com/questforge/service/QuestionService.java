package com.questforge.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.questforge.dto.AdminDto;
import com.questforge.entity.QuestionBank;
import com.questforge.mapper.QuestionBankMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionBankMapper questionBankMapper;

    public Long addOrUpdateQuestion(AdminDto.QuestionReq req) {
        QuestionBank question = BeanUtil.copyProperties(req, QuestionBank.class);
        if (question.getId() == null) {
            questionBankMapper.insert(question);
        } else {
            questionBankMapper.updateById(question);
        }
        return question.getId();
    }

    public void deleteQuestion(Long id) {
        questionBankMapper.deleteById(id);
    }

    public Page<QuestionBank> pageQuestions(int pageNo, int pageSize, Long subjectId, String keyword) {
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        if (subjectId != null) {
            wrapper.eq(QuestionBank::getSubjectId, subjectId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(QuestionBank::getContent, keyword);
        }
        wrapper.orderByDesc(QuestionBank::getCreateTime);
        return questionBankMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }
}