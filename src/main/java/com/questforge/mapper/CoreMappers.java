package com.questforge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.questforge.entity.LearningProject;
import com.questforge.entity.Stage;
import com.questforge.entity.StageItemRef;
import com.questforge.entity.QuestionBank;
import com.questforge.entity.UserStageProgress;
import com.questforge.entity.UserAnswer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 为了演示清晰，将所有核心 Mapper 集中展示。
 */
public interface CoreMappers {

    @Mapper
    interface LearningProjectMapper extends BaseMapper<LearningProject> {}

    @Mapper
    interface StageMapper extends BaseMapper<Stage> {}

    @Mapper
    interface StageItemRefMapper extends BaseMapper<StageItemRef> {}

    @Mapper
    interface QuestionBankMapper extends BaseMapper<QuestionBank> {}

    @Mapper
    interface UserStageProgressMapper extends BaseMapper<UserStageProgress> {}

    @Mapper
    interface UserAnswerMapper extends BaseMapper<UserAnswer> {}
}