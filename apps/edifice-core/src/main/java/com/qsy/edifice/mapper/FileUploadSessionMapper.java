package com.qsy.edifice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsy.edifice.domain.entity.FileUploadSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FileUploadSessionMapper extends BaseMapper<FileUploadSession> {
    @Select("SELECT * FROM file_upload_session WHERE upload_id=#{id} FOR UPDATE")
    FileUploadSession lock(@Param("id") String id);
}
