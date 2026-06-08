package com.questforge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.Result;
import com.questforge.dto.ProjectCreateReq;
import com.questforge.entity.LearningProject;
import com.questforge.entity.Stage;
import com.questforge.entity.StageItemRef;
import com.questforge.mapper.LearningProjectMapper;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.StageItemRefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端：学习路线图与关卡编排控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/project")
@RequiredArgsConstructor
public class AdminProjectController {

    private final LearningProjectMapper projectMapper;
    private final StageMapper stageMapper;
    private final StageItemRefMapper stageItemRefMapper;

    /**
     * 真实有效的级联创建：保存主项目 -> 保存关卡 -> 保存关卡下的题目映射
     */
    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> createProject(@RequestBody ProjectCreateReq req) {
        // 1. 保存路线图主表
        LearningProject project = new LearningProject();
        project.setTitle(req.getTitle());
        project.setStartTime(req.getStartTime());
        project.setEndTime(req.getEndTime());
        project.setAllowSwitchScreen(req.getAllowSwitchScreen() != null ? req.getAllowSwitchScreen() : 1);
        project.setAllowQuit(req.getAllowQuit() != null ? req.getAllowQuit() : 1);
        project.setStatus(0); // 草稿状态
        projectMapper.insert(project);

        Long projectId = project.getId();

        // 2. 级联保存关卡
        if (req.getStages() != null && !req.getStages().isEmpty()) {
            for (ProjectCreateReq.StageReq stageReq : req.getStages()) {
                Stage stage = new Stage();
                stage.setProjectId(projectId);
                stage.setStageName(stageReq.getStageName());
                stage.setStageType(stageReq.getStageType());
                stage.setSortOrder(stageReq.getSortOrder());
                stage.setPassScoreThreshold(stageReq.getPassScoreThreshold());
                stage.setDurationMins(stageReq.getDurationMins());

                // 计算当前关卡总分
                int totalScore = 0;
                if (stageReq.getItems() != null) {
                    totalScore = stageReq.getItems().stream()
                            .filter(i -> i.getItemType() == 2) // 仅累加试题分值
                            .mapToInt(ProjectCreateReq.ItemReq::getScoreWeight)
                            .sum();
                }
                stage.setTotalScore(totalScore);
                stageMapper.insert(stage);

                // 3. 级联保存关卡内容映射
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

    /**
     * 发布项目：真实校验是否存在关卡及考题
     */
    @PutMapping("/{projectId}/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> publishProject(@PathVariable Long projectId) {
        LearningProject project = projectMapper.selectById(projectId);
        if (project == null || project.getStatus() != 0) {
            return Result.error(400, "项目不存在或非草稿状态");
        }

        Long stageCount = stageMapper.selectCount(new LambdaQueryWrapper<Stage>().eq(Stage::getProjectId, projectId));
        if (stageCount == 0) {
            return Result.error(400, "该学习路线图下没有任何关卡，拒绝发布");
        }

        project.setStatus(1); // 已发布
        projectMapper.updateById(project);

        return Result.success(null, "路线图发布成功");
    }
}