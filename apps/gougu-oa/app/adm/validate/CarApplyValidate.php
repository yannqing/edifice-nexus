<?php

declare(strict_types=1);

namespace app\adm\validate;

use think\Validate;

class CarApplyValidate extends Validate
{
    protected $rule = [
        'start_address' => 'require|max:255',
        'destination' => 'require|max:255',
        'use_start_time' => 'require',
        'use_end_time' => 'require',
        'contact_name' => 'require|max:100',
        'contact_phone' => 'require|max:30|regex:/^[0-9+()\- ]{6,30}$/',
        'budget_item' => 'require|max:255',
        'passenger_count' => 'require|integer|between:1,200',
        'business_type' => 'require|max:100',
        'description' => 'require|max:2000',
    ];

    protected $message = [
        'start_address.require' => '请填写始发地',
        'start_address.max' => '始发地不能超过255个字符',
        'destination.require' => '请填写目的地',
        'destination.max' => '目的地不能超过255个字符',
        'use_start_time.require' => '请选择用车开始时间',
        'use_end_time.require' => '请选择用车结束时间',
        'contact_name.require' => '请填写联系人',
        'contact_name.max' => '联系人不能超过100个字符',
        'contact_phone.require' => '请填写联系电话',
        'contact_phone.max' => '联系电话不能超过30个字符',
        'contact_phone.regex' => '联系电话格式不正确',
        'budget_item.require' => '请填写预算事项，如无预算请填写“无”',
        'budget_item.max' => '预算事项不能超过255个字符',
        'passenger_count.require' => '请填写乘车人数',
        'passenger_count.integer' => '乘车人数必须是整数',
        'passenger_count.between' => '乘车人数必须在1到200之间',
        'business_type.require' => '请选择业务类别',
        'business_type.max' => '业务类别不能超过100个字符',
        'description.require' => '请填写用车说明',
        'description.max' => '用车说明不能超过2000个字符',
    ];
}
