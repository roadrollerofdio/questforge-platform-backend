package com.questforge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.questforge.common.Result;
import com.questforge.dto.AdminDto;
import com.questforge.dto.ProjectCreateReq;
import com.questforge.entity.LearningProject;
import com.questforge.entity.QuestionBank;
import com.questforge.entity.Stage;
import com.questforge.entity.StageItemRef;
import com.questforge.mapper.LearningProjectMapper;
import com.questforge.mapper.QuestionBankMapper;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.StageItemRefMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端：学习路线图与关卡编排控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/project")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProjectController {

    private final LearningProjectMapper projectMapper;
    private final StageMapper stageMapper;
    private final StageItemRefMapper stageItemRefMapper;
    private final QuestionBankMapper questionBankMapper;

    @GetMapping("/page")
    public Result<Page<LearningProject>> pageProjects(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<LearningProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(LearningProject::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(LearningProject::getTitle, keyword);
        }
        return Result.success(projectMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    @PutMapping("/status")
    public Result<Void> updateProjectStatus(@RequestBody @Valid ProjectStatusReq req) {
        LearningProject project = projectMapper.selectById(req.getId());
        if (project == null) {
            return Result.error(400, "项目不存在");
        }
        project.setStatus(req.getStatus());
        projectMapper.updateById(project);
        return Result.success();
    }

    @PostMapping("/stage/items")
    public Result<Void> bindStageItem(@RequestBody @Valid StageItemBindReq req) {
        Stage stage = stageMapper.selectById(req.getStageId());
        if (stage == null) {
            return Result.error(400, "关卡不存在");
        }
        StageItemRef ref = new StageItemRef();
        ref.setStageId(req.getStageId());
        ref.setItemId(req.getItemId());
        ref.setItemType(req.getItemType() != null ? req.getItemType() : 2);
        ref.setScoreWeight(req.getScoreWeight() != null ? req.getScoreWeight() : 0);
        ref.setSortNum(req.getSortNum() != null ? req.getSortNum() : 0);
        stageItemRefMapper.insert(ref);

        if (ref.getItemType() == 2) {
            stage.setTotalScore((stage.getTotalScore() != null ? stage.getTotalScore() : 0) + ref.getScoreWeight());
            stageMapper.updateById(stage);
        }
        return Result.success();
    }

    /**
     * 真实有效的级联创建：保存主项目 -> 保存关卡 -> 保存关卡下的题目映射
     */
    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> createProject(@RequestBody ProjectCreateReq req) {
        LearningProject project = new LearningProject();
        project.setTitle(req.getTitle());
        project.setStartTime(req.getStartTime());
        project.setEndTime(req.getEndTime());
        project.setAllowSwitchScreen(req.getAllowSwitchScreen() != null ? req.getAllowSwitchScreen() : 1);
        project.setAllowQuit(req.getAllowQuit() != null ? req.getAllowQuit() : 1);
        project.setTotalScore(100);
        project.setPassScore(60);
        project.setDurationMins(120);
        project.setStatus(0);
        projectMapper.insert(project);

        Long projectId = project.getId();

        if (req.getStages() != null && !req.getStages().isEmpty()) {
            for (ProjectCreateReq.StageReq stageReq : req.getStages()) {
                Stage stage = new Stage();
                stage.setProjectId(projectId);
                stage.setStageName(stageReq.getStageName());
                stage.setStageType(stageReq.getStageType());
                stage.setSortOrder(stageReq.getSortOrder());
                stage.setPassScoreThreshold(stageReq.getPassScoreThreshold());
                stage.setDurationMins(stageReq.getDurationMins());

                int totalScore = 0;
                if (stageReq.getItems() != null) {
                    totalScore = stageReq.getItems().stream()
                            .filter(i -> i.getItemType() == 2)
                            .mapToInt(ProjectCreateReq.ItemReq::getScoreWeight)
                            .sum();
                }
                stage.setTotalScore(totalScore);
                stageMapper.insert(stage);

                if (stageReq.getItems() != null && !stageReq.getItems().isEmpty()) {
                    for (ProjectCreateReq.ItemReq itemReq : stageReq.getItems()) {
                        StageItemRef ref = new StageItemRef();
                        ref.setStageId(stage.getId());
                        ref.setItemId(itemReq.getItemId());
                        ref.setItemType(itemReq.getItemType());
                        ref.setScoreWeight(itemReq.getScoreWeight() != null ? itemReq.getScoreWeight() : 0);
                        ref.setSortNum(itemReq.getSortNum());
                        stageItemRefMapper.insert(ref);
                    }
                }
            }
        }

        log.info("成功创建级联学习路线图，ProjectID: {}", projectId);
        return Result.success(projectId, "学习路线图创建成功");
    }

    @PutMapping("/{projectId}/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> publishProject(@PathVariable Long projectId) {
        LearningProject project = projectMapper.selectById(projectId);
        if (project == null || project.getStatus() != 0) {
            return Result.error(400, "项目不存在或非草稿状态");
        }

        List<Stage> stages = stageMapper.selectList(
                new LambdaQueryWrapper<Stage>().eq(Stage::getProjectId, projectId));
        if (stages.isEmpty()) {
            return Result.error(400, "该学习路线图下没有任何关卡，拒绝发布");
        }

        // 校验每个关卡的及格分不得高于题目总分，否则该关卡永远无法通关、后续关卡将被永久锁死
        List<String> impossibleStages = new ArrayList<>();
        for (Stage stage : stages) {
            int total = stage.getTotalScore() != null ? stage.getTotalScore() : 0;
            int pass = stage.getPassScoreThreshold() != null ? stage.getPassScoreThreshold() : 0;
            if (pass > total) {
                impossibleStages.add(String.format("「%s」(及格 %d 分 > 题目总分 %d 分)", stage.getStageName(), pass, total));
            }
        }
        if (!impossibleStages.isEmpty()) {
            return Result.error(400, "以下关卡的及格分高于题目总分，将无法通关，请调整及格分或补充题目后再发布：" + String.join("；", impossibleStages));
        }

        project.setStatus(1);
        projectMapper.updateById(project);

        return Result.success(null, "路线图发布成功");
    }

    @GetMapping("/{projectId}/stages")
    public Result<List<Stage>> listStages(@PathVariable Long projectId) {
        LearningProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return Result.error(400, "项目不存在");
        }
        List<Stage> stages = stageMapper.selectList(
                new LambdaQueryWrapper<Stage>()
                        .eq(Stage::getProjectId, projectId)
                        .orderByAsc(Stage::getSortOrder));
        return Result.success(stages);
    }

    @PostMapping("/stage/add")
    public Result<Long> addStage(@RequestBody @Valid StageAddReq req) {
        LearningProject project = projectMapper.selectById(req.getProjectId());
        if (project == null) {
            return Result.error(400, "项目不存在");
        }
        Stage stage = new Stage();
        stage.setProjectId(req.getProjectId());
        stage.setStageName(req.getStageName());
        stage.setStageType(req.getStageType() != null ? req.getStageType() : 2);
        stage.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 1);
        stage.setPassScoreThreshold(req.getPassScoreThreshold() != null ? req.getPassScoreThreshold() : 60);
        stage.setDurationMins(req.getDurationMins() != null ? req.getDurationMins() : 60);
        stage.setGemReward(req.getGemReward() != null ? req.getGemReward() : 10);
        stage.setTotalScore(0);
        stageMapper.insert(stage);
        return Result.success(stage.getId(), "关卡添加成功");
    }

    @PutMapping("/stage/{stageId}")
    public Result<Void> updateStage(@PathVariable Long stageId, @RequestBody @Valid StageUpdateReq req) {
        Stage stage = stageMapper.selectById(stageId);
        if (stage == null) {
            return Result.error(400, "关卡不存在");
        }
        if (StringUtils.hasText(req.getStageName())) {
            stage.setStageName(req.getStageName());
        }
        if (req.getSortOrder() != null) {
            stage.setSortOrder(req.getSortOrder());
        }
        if (req.getPassScoreThreshold() != null) {
            stage.setPassScoreThreshold(req.getPassScoreThreshold());
        }
        if (req.getStageType() != null) {
            stage.setStageType(req.getStageType());
        }
        if (req.getDurationMins() != null) {
            stage.setDurationMins(req.getDurationMins());
        }
        if (req.getGemReward() != null) {
            stage.setGemReward(req.getGemReward());
        }
        stageMapper.updateById(stage);
        return Result.success(null, "关卡更新成功");
    }

    @DeleteMapping("/stage/{stageId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteStage(@PathVariable Long stageId) {
        Stage stage = stageMapper.selectById(stageId);
        if (stage == null) {
            return Result.error(400, "关卡不存在");
        }
        stageItemRefMapper.delete(new LambdaQueryWrapper<StageItemRef>().eq(StageItemRef::getStageId, stageId));
        stageMapper.deleteById(stageId);
        return Result.success(null, "关卡已删除");
    }

    @GetMapping("/stage/{stageId}/items")
    public Result<List<AdminDto.StageItemResp>> listStageItems(@PathVariable Long stageId) {
        Stage stage = stageMapper.selectById(stageId);
        if (stage == null) {
            return Result.error(400, "关卡不存在");
        }
        List<StageItemRef> refs = stageItemRefMapper.selectList(
                new LambdaQueryWrapper<StageItemRef>()
                        .eq(StageItemRef::getStageId, stageId)
                        .orderByAsc(StageItemRef::getSortNum));

        List<AdminDto.StageItemResp> result = new ArrayList<>();
        for (StageItemRef ref : refs) {
            AdminDto.StageItemResp item = new AdminDto.StageItemResp();
            item.setRefId(ref.getId());
            item.setItemId(ref.getItemId());
            item.setItemType(ref.getItemType());
            item.setScoreWeight(ref.getScoreWeight());
            item.setSortNum(ref.getSortNum());
            if (ref.getItemType() != null && ref.getItemType() == 2) {
                QuestionBank q = questionBankMapper.selectById(ref.getItemId());
                if (q != null) {
                    item.setContent(q.getContent());
                    item.setQuestionType(q.getType());
                }
            }
            result.add(item);
        }
        return Result.success(result);
    }

    @DeleteMapping("/stage/items/{refId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbindStageItem(@PathVariable Long refId) {
        StageItemRef ref = stageItemRefMapper.selectById(refId);
        if (ref == null) {
            return Result.error(400, "绑定记录不存在");
        }
        if (ref.getItemType() != null && ref.getItemType() == 2) {
            Stage stage = stageMapper.selectById(ref.getStageId());
            if (stage != null) {
                int weight = ref.getScoreWeight() != null ? ref.getScoreWeight() : 0;
                int current = stage.getTotalScore() != null ? stage.getTotalScore() : 0;
                stage.setTotalScore(Math.max(0, current - weight));
                stageMapper.updateById(stage);
            }
        }
        stageItemRefMapper.deleteById(refId);
        return Result.success(null, "已解除绑定");
    }

    @DeleteMapping("/{projectId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteProject(@PathVariable Long projectId) {
        LearningProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return Result.error(400, "项目不存在");
        }
        List<Stage> stages = stageMapper.selectList(
                new LambdaQueryWrapper<Stage>().eq(Stage::getProjectId, projectId));
        for (Stage stage : stages) {
            stageItemRefMapper.delete(new LambdaQueryWrapper<StageItemRef>().eq(StageItemRef::getStageId, stage.getId()));
            stageMapper.deleteById(stage.getId());
        }
        projectMapper.deleteById(projectId);
        return Result.success(null, "学习项目已删除");
    }

    @Data
    public static class StageAddReq {
        @NotNull(message = "项目ID不能为空")
        private Long projectId;
        @NotBlank(message = "关卡名称不能为空")
        private String stageName;
        private Integer stageType;
        private Integer sortOrder;
        private Integer passScoreThreshold;
        private Integer durationMins;
        private Integer gemReward;
    }

    @Data
    public static class StageUpdateReq {
        private String stageName;
        private Integer stageType;
        private Integer sortOrder;
        private Integer passScoreThreshold;
        private Integer durationMins;
        private Integer gemReward;
    }

    @Data
    public static class ProjectStatusReq {
        @NotNull(message = "项目ID不能为空")
        private Long id;
        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    public static class StageItemBindReq {
        @NotNull(message = "关卡ID不能为空")
        private Long stageId;
        @NotNull(message = "内容ID不能为空")
        private Long itemId;
        private Integer itemType;
        private Integer scoreWeight;
        private Integer sortNum;
    }
}
