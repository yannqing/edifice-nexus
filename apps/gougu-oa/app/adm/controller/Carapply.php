<?php

declare(strict_types=1);

namespace app\adm\controller;

use app\adm\model\CarApply as CarApplyModel;
use app\adm\validate\CarApplyValidate;
use app\base\BaseController;
use think\exception\ValidateException;
use think\facade\Db;
use think\facade\View;

class Carapply extends BaseController
{
    protected $model;

    public function __construct()
    {
        parent::__construct();
        $this->model = new CarApplyModel();
    }

    public function datalist()
    {
        $param = get_params();
        if (!request()->isAjax()) {
            View::assign('business_types', $this->businessTypes());
            return view();
        }

        $tab = (int) ($param['tab'] ?? 0);
        if (!in_array($tab, [0, 1, 2, 3, 4], true)) {
            $tab = 0;
        }
        $uid = (int) $this->uid;
        $where = [['delete_time', '=', 0]];
        $whereOr = [];
        $isOfficeAdmin = isAuth($uid, 'office_admin', 'conf_1') === 1;

        if ($tab === 0 && !$isOfficeAdmin) {
            $whereOr[] = ['admin_id', '=', $uid];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_uids)")];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_history_uids)")];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_copy_uids)")];
        } elseif ($tab === 1) {
            $where[] = ['admin_id', '=', $uid];
        } elseif ($tab === 2) {
            $where[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_uids)")];
        } elseif ($tab === 3) {
            $where[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_history_uids)")];
        } elseif ($tab === 4) {
            $where[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_copy_uids)")];
        }

        if (isset($param['check_status']) && $param['check_status'] !== '') {
            $where[] = ['check_status', '=', (int) $param['check_status']];
        }
        if (!empty($param['business_type'])) {
            $where[] = ['business_type', '=', trim((string) $param['business_type'])];
        }
        if (!empty($param['keywords'])) {
            $where[] = [
                'id|start_address|destination|contact_name|contact_phone|business_type|description',
                'like',
                '%' . trim((string) $param['keywords']) . '%',
            ];
        }

        $list = $this->model->datalist($where, $whereOr, $param);
        return table_assign(0, '', $list);
    }

    public function add()
    {
        $param = get_params();
        $id = (int) ($param['id'] ?? 0);
        if ($id === 0 && !$this->canApply()) {
            if (request()->isAjax()) {
                return to_assign(1, '当前部门不能使用用车申请');
            }
            throw new \think\exception\HttpException(403, '当前部门不能使用用车申请');
        }
        if (request()->isAjax()) {
            $param = $this->normalizeInput($param);
            try {
                validate(CarApplyValidate::class)->check($param);
            } catch (ValidateException $e) {
                return to_assign(1, $e->getError());
            }

            $businessTypes = array_column($this->businessTypes(), 'title');
            if (!in_array($param['business_type'], $businessTypes, true)) {
                return to_assign(1, '请选择有效的业务类别');
            }

            $startTime = strtotime($param['use_start_time']);
            $endTime = strtotime($param['use_end_time']);
            if ($startTime === false || $endTime === false) {
                return to_assign(1, '用车时间格式不正确');
            }
            if ($endTime <= $startTime) {
                return to_assign(1, '用车结束时间必须晚于开始时间');
            }
            $param['use_start_time'] = $startTime;
            $param['use_end_time'] = $endTime;
            $param['did'] = (int) $this->did;

            if (!empty($param['id'])) {
                $detail = $this->model->getById((int) $param['id']);
                $error = $this->editableError($detail);
                if ($error !== '') {
                    return to_assign(1, $error);
                }
                return $this->model->edit($param, (int) $this->uid);
            }

            $param['admin_id'] = (int) $this->uid;
            return $this->model->add($param);
        }

        View::assign([
            'business_types' => $this->businessTypes(),
            'user' => get_admin($this->uid),
        ]);
        if ($id > 0) {
            $detail = $this->model->getById($id);
            $error = $this->editableError($detail);
            if ($error !== '') {
                throw new \think\exception\HttpException(empty($detail) ? 404 : 403, $error);
            }
            View::assign('detail', $detail);
        }
        if (is_mobile()) {
            return view('qiye@/approve/add_car_apply');
        }
        return view();
    }

    public function view($id)
    {
        $detail = $this->model->getById((int) $id);
        if (empty($detail)) {
            throw new \think\exception\HttpException(404, '用车申请不存在或已删除');
        }
        if (!$this->canView($detail)) {
            throw new \think\exception\HttpException(403, '无权限查看该用车申请');
        }
        View::assign([
            'detail' => $detail,
            'create_user' => get_admin((int) $detail['admin_id']),
        ]);
        if (is_mobile()) {
            return view('qiye@/approve/view_car_apply');
        }
        return view();
    }

    public function del()
    {
        if (!request()->isDelete()) {
            return to_assign(1, '错误的请求');
        }
        $id = (int) request()->param('id', 0);
        $detail = $this->model->getById($id);
        $error = $this->editableError($detail);
        if ($error !== '') {
            return to_assign(1, $error);
        }
        return $this->model->delById($id, (int) $this->uid);
    }

    private function businessTypes(): array
    {
        return Db::name('BasicAdm')
            ->where(['types' => 3, 'status' => 1])
            ->order('id asc')
            ->select()
            ->toArray();
    }

    private function canApply(): bool
    {
        $did = (int) $this->did;
        $category = Db::name('FlowCate')
            ->where([
                'name' => 'car_apply',
                'status' => 1,
                'is_list' => 1,
            ])
            ->where(function ($query) use ($did) {
                $query->where('department_ids', '')
                    ->whereOrRaw("FIND_IN_SET('{$did}',department_ids)");
            })
            ->find();
        if (empty($category)) {
            return false;
        }

        return Db::name('Flow')
            ->where([
                'cate_id' => (int) $category['id'],
                'status' => 1,
                'delete_time' => 0,
            ])
            ->where(function ($query) use ($did) {
                $query->where('department_ids', '')
                    ->whereOrRaw("FIND_IN_SET('{$did}',department_ids)");
            })
            ->count() > 0;
    }

    private function normalizeInput(array $param): array
    {
        $stringFields = [
            'start_address',
            'destination',
            'use_start_time',
            'use_end_time',
            'contact_name',
            'contact_phone',
            'budget_item',
            'business_type',
            'description',
            'file_ids',
        ];
        foreach ($stringFields as $field) {
            $param[$field] = isset($param[$field]) && is_scalar($param[$field])
                ? trim((string) $param[$field])
                : '';
        }
        $param['id'] = (int) ($param['id'] ?? 0);
        $param['passenger_count'] = (int) ($param['passenger_count'] ?? 0);
        return $param;
    }

    private function editableError($detail): string
    {
        if (empty($detail)) {
            return '用车申请不存在或已删除';
        }
        if ((int) $detail['admin_id'] !== (int) $this->uid) {
            return '无权限编辑他人的用车申请';
        }
        if (!in_array((int) $detail['check_status'], [0, 4], true)) {
            return '当前审批状态不允许编辑或删除';
        }
        return '';
    }

    private function canView($detail): bool
    {
        if ((int) $detail['admin_id'] === (int) $this->uid || isAuth($this->uid, 'office_admin', 'conf_1') === 1) {
            return true;
        }
        foreach (['check_uids', 'check_history_uids', 'check_copy_uids'] as $field) {
            $uids = array_filter(explode(',', (string) ($detail[$field] ?? '')));
            if (in_array((string) $this->uid, $uids, true)) {
                return true;
            }
        }
        return false;
    }
}
