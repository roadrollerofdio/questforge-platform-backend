package com.questforge.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.dto.AdminDto;
import com.questforge.entity.ExamPaper;
import com.questforge.entity.ExamPaperQuestion;
import com.questforge.entity.ExamQuestion;
import com.questforge.mapper.CoreMappers.ExamPaperMapper;
import com.questforge.mapper.CoreMappers.ExamPaperQuestionMapper;
import com.questforge.mapper.CoreMappers.ExamQuestionMapper;
import com.questforge.service.strategy.PaperGenerationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperService {

    private final ExamPaperMapper examPaperMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final PaperGenerationStrategy randomGenerationStrategy;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public Long createManualPaper(AdminDto.PaperCreateReq req) {
        ExamPaper paper = BeanUtil.copyProperties(req, ExamPaper.class);
        paper.setPaperStatus(0);
        int totalScore = req.getQuestionList().stream().mapToInt(AdminDto.PaperCreateReq.PaperQuestionItem::getItemScore).sum();
        paper.setTotalScore(totalScore);
        examPaperMapper.insert(paper);

        for (AdminDto.PaperCreateReq.PaperQuestionItem item : req.getQuestionList()) {
            ExamPaperQuestion pq = new ExamPaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(item.getQuestionId());
            pq.setItemScore(item.getItemScore());
            pq.setSortNum(item.getSortNum());
            examPaperQuestionMapper.insert(pq);
        }
        return paper.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRandomPaper(AdminDto.PaperRandomCreateReq req) {
        ExamPaper paper = BeanUtil.copyProperties(req, ExamPaper.class);
        paper.setPaperStatus(0);
        paper.setTotalScore(0); // 暂定为 0
        examPaperMapper.insert(paper);

        randomGenerationStrategy.generate(paper.getId(), req.getSubjectId(), req.getRuleParams());

        // [Fix P1-5] 智能抽题结束后，重新汇总并回写精准的 TotalScore
        List<ExamPaperQuestion> pqs = examPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, paper.getId()));
        int actualTotal = pqs.stream().mapToInt(ExamPaperQuestion::getItemScore).sum();
        paper.setTotalScore(actualTotal);
        examPaperMapper.updateById(paper);

        return paper.getId();
    }

    public void publishPaper(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null || paper.getPaperStatus() != 0) throw new RuntimeException("试卷不存在或已发布");

        List<ExamPaperQuestion> pqList = examPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, paperId).orderByAsc(ExamPaperQuestion::getSortNum));
        List<Long> questionIds = pqList.stream().map(ExamPaperQuestion::getQuestionId).toList();
        if (questionIds.isEmpty()) throw new RuntimeException("试卷中没有题目，无法发布");

        List<ExamQuestion> questions = examQuestionMapper.selectBatchIds(questionIds);
        Map<Long, ExamQuestion> questionMap = questions.stream().collect(Collectors.toMap(ExamQuestion::getId, q -> q));

        Map<String, Object> paperData = new HashMap<>();
        paperData.put("paperId", paper.getId().toString());
        paperData.put("title", paper.getTitle());
        paperData.put("durationMins", paper.getDurationMins());
        paperData.put("totalScore", paper.getTotalScore());

        List<Map<String, Object>> safeQuestionList = new ArrayList<>();
        for (ExamPaperQuestion pq : pqList) {
            ExamQuestion q = questionMap.get(pq.getQuestionId());
            if (q != null) {
                Map<String, Object> safeQ = new HashMap<>();
                safeQ.put("questionId", q.getId().toString());
                safeQ.put("questionType", q.getQuestionType());
                safeQ.put("content", q.getContent());
                safeQ.put("options", q.getOptionsJson());
                safeQ.put("score", pq.getItemScore());
                safeQuestionList.add(safeQ);
            }
        }
        paperData.put("questions", safeQuestionList);

        // [Fix P0-3] 动态测算缓存存活期
        long ttlMinutes = 7 * 24 * 60; // 默认给7天
        if (paper.getExamEndTime() != null) {
            ttlMinutes = java.time.Duration.between(LocalDateTime.now(), paper.getExamEndTime()).toMinutes() + 30;
            if (ttlMinutes <= 0) ttlMinutes = 60; // 若发布时已晚，强制给1小时兜底
        }

        String cacheKey = RedisConsts.PAPER_INFO_PREFIX + paperId;
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(paperData), ttlMinutes, TimeUnit.MINUTES);

        paper.setPaperStatus(1);
        examPaperMapper.updateById(paper);
    }

    // 强制提前结束考试
    public void endPaper(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper != null && paper.getPaperStatus() == 1) {
            paper.setPaperStatus(2); // 标记为已下线
            examPaperMapper.updateById(paper);
            redisTemplate.delete(RedisConsts.PAPER_INFO_PREFIX + paperId); // 删除缓存防止继续拉取
        }
    }

    // 获取试卷详情 (后台查看明细，包含答案)
    public Map<String, Object> getPaperDetailAdmin(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        List<ExamPaperQuestion> pqList = examPaperQuestionMapper.selectList(new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, paperId).orderByAsc(ExamPaperQuestion::getSortNum));
        List<Long> qIds = pqList.stream().map(ExamPaperQuestion::getQuestionId).toList();
        List<ExamQuestion> qs = qIds.isEmpty() ? new ArrayList<>() : examQuestionMapper.selectBatchIds(qIds);
        Map<Long, ExamQuestion> qMap = qs.stream().collect(Collectors.toMap(ExamQuestion::getId, q->q));

        Map<String, Object> res = new HashMap<>();
        res.put("paper", paper);
        List<Map<String, Object>> qDetails = new ArrayList<>();
        for (ExamPaperQuestion pq : pqList) {
            ExamQuestion q = qMap.get(pq.getQuestionId());
            if (q != null) {
                Map<String, Object> qd = new HashMap<>();
                qd.put("questionId", q.getId().toString());
                qd.put("content", q.getContent());
                qd.put("optionsJson", q.getOptionsJson());
                qd.put("standardAnswer", q.getStandardAnswer());
                qd.put("itemScore", pq.getItemScore());
                qd.put("questionType", q.getQuestionType());
                qDetails.add(qd);
            }
        }
        res.put("questions", qDetails);
        return res;
    }
}