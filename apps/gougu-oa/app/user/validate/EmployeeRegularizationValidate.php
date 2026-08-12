<?php

declare(strict_types=1);

namespace app\user\validate;

use think\Validate;

class EmployeeRegularizationValidate extends Validate
{
    protected $rule = [
        'applicant_name' => 'require|max:100',
        'position_id' => 'require|integer|gt:0',
        'position_name' => 'require|max:100',
        'probation_start_date' => 'require|date',
        'probation_end_date' => 'require|date',
        'work_summary' => 'require|max:10000',
        'main_achievements' => 'max:10000',
    ];

    protected $message = [
        'applicant_name.require' => '申请人不能为空',
        'applicant_name.max' => '申请人姓名不能超过100个字符',
        'position_id.require' => '员工档案未设置岗位',
        'position_id.integer' => '岗位信息不正确',
        'position_id.gt' => '员工档案未设置岗位',
        'position_name.require' => '员工档案未设置岗位',
        'position_name.max' => '岗位名称不能超过100个字符',
        'probation_start_date.require' => '请选择试用开始日期',
        'probation_start_date.date' => '试用开始日期格式不正确',
        'probation_end_date.require' => '请选择试用结束日期',
        'probation_end_date.date' => '试用结束日期格式不正确',
        'work_summary.require' => '请填写工作总结',
        'work_summary.max' => '工作总结不能超过10000个字符',
        'main_achievements.max' => '主要业绩不能超过10000个字符',
    ];
}
