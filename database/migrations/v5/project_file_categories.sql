-- 项目文件分类调整："报告"更名为"成果文件"，新增"计算底稿"分类。

SET NAMES utf8mb4;

UPDATE project_files
SET file_category = '成果文件'
WHERE file_category = '报告';

ALTER TABLE project_files
    MODIFY COLUMN file_category varchar(64) NULL
    COMMENT '文件分类：图纸/合同/成果文件/计算底稿/其他';
