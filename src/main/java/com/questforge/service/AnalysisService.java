package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.dto.AdminDto;
import com.questforge.dto.AnalysisDto;
import com.questforge.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final ExamPaperMapper examPaperMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamUserAnswerMapper examUserAnswerMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取用户待考列表
     */
    public List<Map<String, Object>> getPendingPapers(Long userId) {
        List<ExamPaper> publishedPapers = examPaperMapper.selectList(new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getPaperStatus, 1));
        List<ExamRecord> userRecords = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getUserId, userId));
        Set<Long> attendedPaperIds = userRecords.stream().map(ExamRecord::getPaperId).collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();

        return publishedPapers.stream()
                .filter(p -> !attendedPaperIds.contains(p.getId()))
                .filter(p -> p.getExamEndTime() != null && now.isBefore(p.getExamEndTime())) // 必须没过截止时间
                .map(p -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("paperId", p.getId().toString());
                    dto.put("title", p.getTitle());
                    dto.put("durationMins", p.getDurationMins());
                    dto.put("totalScore", p.getTotalScore());
                    dto.put("examStartTime", p.getExamStartTime());
                    dto.put("examEndTime", p.getExamEndTime());
                    dto.put("allowQuit", p.getAllowQuit() == 1);
                    dto.put("allowSwitchScreen", p.getAllowSwitchScreen() == 1);
                    dto.put("isStarted", !now.isBefore(p.getExamStartTime()));
                    return dto;
                }).collect(Collectors.toList());
    }

    /**
     * 管理端：获取单场考试分析大盘统计数据
     */
    public AnalysisDto.DashboardResp getExamStatistics(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        List<ExamRecord> records = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getPaperId, paperId).eq(ExamRecord::getExamStatus, 1));

        AnalysisDto.DashboardResp resp = new AnalysisDto.DashboardResp();

        int currentStatus = paper.getPaperStatus();
        // 准确判断考试是否结束
        if (currentStatus == 1 && paper.getExamEndTime() != null) {
            if (LocalDateTime.now().isAfter(paper.getExamEndTime())) {
                currentStatus = 2;
            }
        }
        resp.setPaperStatus(currentStatus);

        int totalParticipants = records.size();
        resp.setTotalParticipants(totalParticipants);

        if (totalParticipants == 0) {
            resp.setAverageScore(0.0); resp.setHighestScore(0); resp.setPassRate("0%");
            return resp;
        }

        int maxScore = 0, sumScore = 0, passCount = 0;
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("0-59", 0); dist.put("60-79", 0); dist.put("80-89", 0); dist.put("90-100", 0);

        for (ExamRecord r : records) {
            int s = r.getTotalScore() == null ? 0 : r.getTotalScore();
            sumScore += s; maxScore = Math.max(maxScore, s);
            if (s >= paper.getPassScore()) passCount++;
            if (s < 60) dist.put("0-59", dist.get("0-59") + 1);
            else if (s < 80) dist.put("60-79", dist.get("60-79") + 1);
            else if (s < 90) dist.put("80-89", dist.get("80-89") + 1);
            else dist.put("90-100", dist.get("90-100") + 1);
        }

        resp.setHighestScore(maxScore);
        resp.setAverageScore(new BigDecimal((double) sumScore / totalParticipants).setScale(1, RoundingMode.HALF_UP).doubleValue());
        resp.setPassRate(new BigDecimal((double) passCount / totalParticipants * 100).setScale(1, RoundingMode.HALF_UP) + "%");
        resp.setScoreDistribution(dist);
        return resp;
    }

    /**
     * 获取用户单场考试报表 (大字报)
     */
    public AnalysisDto.ReportResp getUserReport(Long recordId, Long userId) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) throw new RuntimeException("无权查看该成绩单");
        if (record.getExamStatus() == 0) throw new RuntimeException("考试尚未完成，无法查看成绩");

        AnalysisDto.ReportResp resp = new AnalysisDto.ReportResp();
        resp.setTotalScore(record.getTotalScore());

        Long totalCount = examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getPaperId, record.getPaperId()));
        Long lowerCount = examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getPaperId, record.getPaperId()).lt(ExamRecord::getTotalScore, record.getTotalScore()));

        if (totalCount > 1) {
            double percent = (double) lowerCount / (totalCount - 1) * 100;
            resp.setBeatPercentage(String.format("%.1f%%", percent));
        } else {
            resp.setBeatPercentage("100%");
        }

        List<ExamUserAnswer> wrongAnswers = examUserAnswerMapper.selectList(new LambdaQueryWrapper<ExamUserAnswer>().eq(ExamUserAnswer::getRecordId, recordId).eq(ExamUserAnswer::getIsCorrect, 0));
        List<AnalysisDto.ReportResp.WrongQuestionDetail> wrongDetails = new ArrayList<>();
        if (!wrongAnswers.isEmpty()) {
            List<Long> qIds = wrongAnswers.stream().map(ExamUserAnswer::getQuestionId).toList();
            Map<Long, ExamQuestion> qMap = examQuestionMapper.selectBatchIds(qIds).stream().collect(Collectors.toMap(ExamQuestion::getId, q -> q));
            for (ExamUserAnswer wa : wrongAnswers) {
                ExamQuestion q = qMap.get(wa.getQuestionId());
                if (q != null) {
                    AnalysisDto.ReportResp.WrongQuestionDetail d = new AnalysisDto.ReportResp.WrongQuestionDetail();
                    d.setQuestionId(q.getId().toString());
                    d.setContent(q.getContent());
                    d.setUserAnswer(wa.getUserAnswer());
                    d.setStandardAnswer(q.getStandardAnswer());
                    d.setAnalysis(q.getAnalysis());
                    wrongDetails.add(d);
                }
            }
        }
        resp.setWrongQuestions(wrongDetails);
        return resp;
    }

    /**
     * 从 Redis 获取 Top 20 排行榜
     */
    public List<AnalysisDto.LeaderboardResp> getTop20Leaderboard(Long paperId) {
        String key = RedisConsts.LEADERBOARD_PREFIX + paperId;
        Set<ZSetOperations.TypedTuple<Object>> topTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 19);

        List<AnalysisDto.LeaderboardResp> list = new ArrayList<>();
        if (topTuples == null || topTuples.isEmpty()) return list;

        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : topTuples) {
            AnalysisDto.LeaderboardResp dto = new AnalysisDto.LeaderboardResp();
            dto.setRank(rank++);
            dto.setScore(tuple.getScore() != null ? tuple.getScore().intValue() : 0);
            String uIdStr = (String) tuple.getValue();
            dto.setUserId(uIdStr);
            SysUser user = sysUserMapper.selectById(Long.parseLong(uIdStr));
            dto.setRealName(user != null ? user.getRealName() : "神秘考生");
            list.add(dto);
        }
        return list;
    }


    /**
     * 【新增】：获取管理端控制台首页全局统计数据
     */
    public AdminDto.DashboardSummaryResp getDashboardSummary() {
        AdminDto.DashboardSummaryResp summary = new AdminDto.DashboardSummaryResp();

        // 题库总题数
        summary.setTotalQuestions(examQuestionMapper.selectCount(new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getIsDeleted, 0)));
        // 试卷总数
        summary.setTotalPapers(examPaperMapper.selectCount(new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getIsDeleted, 0)));
        // 累计考试人次
        summary.setTotalExams(examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()));
        // 系统注册考生数
        summary.setActiveUsers(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleCode, "USER")));

        return summary;
    }
}