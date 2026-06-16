package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.UserMessageRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMessageReadMapper extends BaseMapper<UserMessageRead> {

    int insertIgnore(UserMessageRead entity);

    int insertIgnoreBatch(@Param("rows") List<UserMessageRead> rows);
}
