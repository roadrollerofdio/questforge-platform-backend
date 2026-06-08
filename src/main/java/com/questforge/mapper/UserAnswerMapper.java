package com.questforge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.questforge.entity.UserAnswer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAnswerMapper extends BaseMapper<UserAnswer> {
}