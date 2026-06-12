package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.dto.AdminDto;
import com.questforge.dto.AnalysisDto;
import com.questforge.entity.LearningProject;
import com.questforge.entity.QuestionBank;
import com.questforge.entity.Stage;
import com.questforge.entity.SysUser;
import com.questforge.entity.UserAnswer;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.ExamRecordMapper;
import com.questforge.mapper.LearningProjectMapper;
import com.questforge.mapper.QuestionBankMapper;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.SysUserMapper;
import com.questforge.mapper.UserAnswerMapper;
import com.questforge.mapper.UserStageProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import com.questforge.common.RedisConsts;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习项目数据分析与报表服务 (真实有效版)
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final UserStageProgressMapper progressMapper;
    private final SysUserMapper sysUserMapper;
    private final QuestionBankMapper questionBankMapper;
    private final LearningProjectMapper projectMapper;
    private final ExamRecordMapper examRecordMapper;
    private final StageMapper stageMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public AdminDto.DashboardSummaryResp getDashboardSummary() {
        AdminDto.DashboardSummaryResp resp = new AdminDto.DashboardSummaryResp();
        resp.setTotalQuestions(questionBankMapper.selectCount(null));
        resp.setTotalPapers(projectMapper.selectCount(null));
        resp.setTotalExams(examRecordMapper.selectCount(null));
        resp.setActiveUsers(sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1).eq(SysUser::getIsDeleted, 0)));
        return resp;
    }

    /**
     * 真实获取单场学习计划的宏观统计大盘
     */
    public AnalysisDto.ProjectStatsResp getProjectStatistics(Long projectId) {
        // 1. 获取该项目下所有的关卡进度流转记录
        List<UserStageProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<UserStageProgress>().eq(UserStageProgress::getProjectId, projectId)
        );

        // 2. 统计参与总人数 (依据 userId 去重)
        long totalUsers = allProgress.stream().map(UserStageProgress::getUserId).distinct().count();

        // 3. 计算平均分
        double avgScore = allProgress.stream().mapToInt(p -> p.getCurrentScore() == null ? 0 : p.getCurrentScore()).average().orElse(0.0);

        // 4. 计算通关率 (状态为 4-已通关 的人数)
        long passedCount = allProgress.stream().filter(p -> p.getStatus() == 4).map(UserStageProgress::getUserId).distinct().count();
        String passRate = totalUsers == 0 ? "0%" : String.format("%.1f%%", (double) passedCount / totalUsers * 100);

        // 5. 计算分数段分布情况
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("0-59", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() < 60).count());
        distribution.put("60-79", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() >= 60 && p.getCurrentScore() < 80).count());
        distribution.put("80-89", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() >= 80 && p.getCurrentScore() < 90).count());
        distribution.put("90-100", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() >= 90).count());

        AnalysisDto.ProjectStatsResp resp = new AnalysisDto.ProjectStatsResp();
        resp.setTotalParticipants(totalUsers);
        resp.setAverageScore(avgScore);
        resp.setPassRate(passRate);
        resp.setScoreDistribution(distribution);
        return resp;
    }

    /**
     * 获取项目下每个学员的学习情况明细 (各关卡得分 + 错题清单)
     */
    public AnalysisDto.ProjectLearningDetailResp getProjectLearningDetail(Long projectId) {
        AnalysisDto.ProjectLearningDetailResp resp = new AnalysisDto.ProjectLearningDetailResp();
        resp.setProjectId(projectId);

        LearningProject project = projectMapper.selectById(projectId);
        resp.setProjectTitle(project != null ? project.getTitle() : "未知项目");

        // 1. 项目关卡序列
        List<Stage> stages = stageMapper.selectList(
                new LambdaQueryWrapper<Stage>().eq(Stage::getProjectId, projectId).orderByAsc(Stage::getSortOrder));
        resp.setStages(stages.stream().map(s -> {
            AnalysisDto.StageMetaResp meta = new AnalysisDto.StageMetaResp();
            meta.setStageId(s.getId());
            meta.setStageName(s.getStageName());
            meta.setSortOrder(s.getSortOrder());
            meta.setStageType(s.getStageType());
            meta.setPassScoreThreshold(s.getPassScoreThreshold());
            return meta;
        }).collect(Collectors.toList()));

        // 2. 所有学员的关卡进度
        List<UserStageProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<UserStageProgress>().eq(UserStageProgress::getProjectId, projectId));

        // 3. 批量捞取错题记录 (按 progressId 关联)
        Map<Long, List<UserAnswer>> wrongAnswersByProgress = Collections.emptyMap();
        Map<Long, QuestionBank> questionMap = Collections.emptyMap();
        if (!allProgress.isEmpty()) {
            List<Long> progressIds = allProgress.stream().map(UserStageProgress::getId).collect(Collectors.toList());
            List<UserAnswer> wrongAnswers = userAnswerMapper.selectList(
                    new LambdaQueryWrapper<UserAnswer>()
                            .in(UserAnswer::getProgressId, progressIds)
                            .eq(UserAnswer::getIsCorrect, 0));
            wrongAnswersByProgress = wrongAnswers.stream().collect(Collectors.groupingBy(UserAnswer::getProgressId));

            List<Long> questionIds = wrongAnswers.stream().map(UserAnswer::getQuestionId).distinct().collect(Collectors.toList());
            if (!questionIds.isEmpty()) {
                questionMap = questionBankMapper.selectBatchIds(questionIds).stream()
                        .collect(Collectors.toMap(QuestionBank::getId, q -> q));
            }
        }

        // 4. 按学员分组组装明细
        Map<Long, List<UserStageProgress>> progressByUser = allProgress.stream()
                .collect(Collectors.groupingBy(UserStageProgress::getUserId, LinkedHashMap::new, Collectors.toList()));

        List<AnalysisDto.StudentLearningResp> students = new ArrayList<>();
        double totalScoreSum = 0;
        long scoredCount = 0;

        for (Map.Entry<Long, List<UserStageProgress>> entry : progressByUser.entrySet()) {
            Long userId = entry.getKey();
            List<UserStageProgress> userProgress = entry.getValue();
            SysUser user = sysUserMapper.selectById(userId);

            AnalysisDto.StudentLearningResp student = new AnalysisDto.StudentLearningResp();
            student.setUserId(userId);
            student.setUsername(user != null ? user.getUsername() : "未知账号");
            student.setRealName(user != null ? user.getRealName() : "匿名学员");
            student.setTotalStages(stages.size());

            Map<Long, UserStageProgress> progressByStage = userProgress.stream()
                    .collect(Collectors.toMap(UserStageProgress::getStageId, p -> p, (a, b) -> a));

            List<AnalysisDto.StudentStageResp> stageDetails = new ArrayList<>();
            int passedStages = 0;
            int wrongCount = 0;
            double scoreSum = 0;
            int scoredStages = 0;

            for (Stage stage : stages) {
                UserStageProgress p = progressByStage.get(stage.getId());

                AnalysisDto.StudentStageResp sd = new AnalysisDto.StudentStageResp();
                sd.setStageId(stage.getId());
                sd.setStageName(stage.getStageName());
                sd.setStatus(p != null ? p.getStatus() : 0);
                sd.setScore(p != null ? p.getCurrentScore() : null);
                sd.setWrongQuestions(new ArrayList<>());

                if (p != null) {
                    if (p.getStatus() != null && p.getStatus() == 4) passedStages++;
                    if (p.getCurrentScore() != null) {
                        scoreSum += p.getCurrentScore();
                        scoredStages++;
                    }
                    for (UserAnswer wa : wrongAnswersByProgress.getOrDefault(p.getId(), Collections.emptyList())) {
                        QuestionBank q = questionMap.get(wa.getQuestionId());
                        AnalysisDto.WrongQuestionResp wq = new AnalysisDto.WrongQuestionResp();
                        wq.setQuestionId(wa.getQuestionId());
                        wq.setContent(q != null ? q.getContent() : "（题目已删除）");
                        wq.setUserAnswer(wa.getUserAnswer());
                        wq.setStandardAnswer(q != null ? q.getAnswer() : "-");
                        sd.getWrongQuestions().add(wq);
                        wrongCount++;
                    }
                }
                stageDetails.add(sd);
            }

            student.setStages(stageDetails);
            student.setPassedStages(passedStages);
            student.setTotalWrongCount(wrongCount);
            double avg = scoredStages == 0 ? 0.0 : scoreSum / scoredStages;
            student.setAverageScore(Math.round(avg * 10) / 10.0);

            if (scoredStages > 0) {
                totalScoreSum += avg;
                scoredCount++;
            }
            students.add(student);
        }

        // 按平均分降序，方便管理员快速定位头尾部学员
        students.sort(Comparator.comparing(AnalysisDto.StudentLearningResp::getAverageScore, Comparator.reverseOrder()));

        resp.setStudents(students);
        resp.setTotalParticipants((long) students.size());
        resp.setAverageScore(scoredCount == 0 ? 0.0 : Math.round(totalScoreSum / scoredCount * 10) / 10.0);
        return resp;
    }

    /**
     * 为 AI 分析员构建项目学情上下文 (纯文本摘要，控制长度防止 Prompt 溢出)
     */
    public String buildAiAnalysisContext(Long projectId) {
        AnalysisDto.ProjectLearningDetailResp detail = getProjectLearningDetail(projectId);
        AnalysisDto.ProjectStatsResp stats = getProjectStatistics(projectId);

        StringBuilder sb = new StringBuilder();
        sb.append("【项目名称】").append(detail.getProjectTitle()).append('\n');
        sb.append("【参与学员数】").append(detail.getTotalParticipants())
                .append("，【全员平均分】").append(detail.getAverageScore())
                .append("，【通关率】").append(stats.getPassRate()).append('\n');
        sb.append("【分数段分布】").append(stats.getScoreDistribution()).append('\n');
        sb.append("【关卡序列】");
        detail.getStages().forEach(s -> sb.append(s.getStageName()).append("(及格").append(s.getPassScoreThreshold()).append("分) "));
        sb.append('\n');

        // 最多列出前 30 名学员的明细，防止上下文超长
        int limit = Math.min(detail.getStudents().size(), 30);
        for (int i = 0; i < limit; i++) {
            AnalysisDto.StudentLearningResp stu = detail.getStudents().get(i);
            sb.append("学员[").append(stu.getRealName()).append('/').append(stu.getUsername()).append("] ")
                    .append("平均分:").append(stu.getAverageScore())
                    .append(" 通关:").append(stu.getPassedStages()).append('/').append(stu.getTotalStages())
                    .append(" 错题数:").append(stu.getTotalWrongCount()).append(" | ");
            for (AnalysisDto.StudentStageResp st : stu.getStages()) {
                sb.append(st.getStageName()).append(':')
                        .append(st.getScore() == null ? "未考" : st.getScore() + "分");
                if (!st.getWrongQuestions().isEmpty()) {
                    sb.append("(错").append(st.getWrongQuestions().size()).append("题:");
                    st.getWrongQuestions().stream().limit(3).forEach(wq -> {
                        String content = wq.getContent() == null ? "" : wq.getContent();
                        sb.append(content, 0, Math.min(content.length(), 30)).append(';');
                    });
                    sb.append(')');
                }
                sb.append(' ');
            }
            sb.append('\n');
        }
        if (detail.getStudents().size() > limit) {
            sb.append("...(其余 ").append(detail.getStudents().size() - limit).append(" 名学员略)\n");
        }
        return sb.toString();
    }

    /**
     * 真实获取单场学习计划的动态排行榜 (基于 Redis ZSet)
     */
    public List<AnalysisDto.LeaderboardResp> getLeaderboard(Long projectId) {
        String key = RedisConsts.LEADERBOARD_PREFIX + projectId;

        // 获取 Redis 中排名前 20 的分数集合 (分数从大到小)
        Set<ZSetOperations.TypedTuple<Object>> topUsers = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 19);
        List<AnalysisDto.LeaderboardResp> result = new ArrayList<>();

        if (topUsers == null || topUsers.isEmpty()) {
            return result;
        }

        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : topUsers) {
            Long userId = Long.valueOf(tuple.getValue().toString());
            SysUser user = sysUserMapper.selectById(userId);

            AnalysisDto.LeaderboardResp item = new AnalysisDto.LeaderboardResp();
            item.setRank(rank++);
            item.setUserId(userId);
            item.setUsername(user != null ? user.getUsername() : "未知账号");
            item.setRealName(user != null ? user.getRealName() : "匿名冒险者");
            item.setScore(tuple.getScore() != null ? tuple.getScore().intValue() : 0);
            result.add(item);
        }
        return result;
    }
}