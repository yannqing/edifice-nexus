<?php

declare(strict_types=1);

namespace app\user\controller;

use app\base\BaseController;
use app\user\model\EmployeeRegularization as EmployeeRegularizationModel;
use app\user\validate\EmployeeRegularizationValidate;
use think\exception\ValidateException;
use think\facade\Db;
use think\facade\View;

class Regularization extends BaseController
{
    protected $model;

    public function __construct()
    {
        parent::__construct();
        $this->model = new EmployeeRegularizationModel();
    }

    public function datalist()
    {
        $param = get_params();
        if (!request()->isAjax()) {
            return view();
        }

        $tab = (int) ($param['tab'] ?? 0);
        if (!in_array($tab, [0, 1, 2, 3, 4], true)) {
            $tab = 0;
        }

        $uid = (int) $this->uid;
        $where = [['delete_time', '=', 0]];
        $whereOr = [];
        if ($tab === 0 && !$this->isPersonnelAdmin()) {
            $whereOr[] = ['admin_id', '=', $uid];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_uids)")];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_history_uids)")];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$uid}',check_copy_uids)")];
            $departmentIds = $this->managedDepartmentIds();
            if (!empty($departmentIds)) {
                $whereOr[] = ['did', 'in', $departmentIds];
            }
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
        if (!empty($param['keywords'])) {
            $keywords = trim((string) $param['keywords']);
            $where[] = [
                'applicant_name|department_name|position_name|work_summary|main_achievements',
                'like',
                '%' . $keywords . '%',
            ];
        }

        return table_assign(0, '', $this->model->datalist($where, $whereOr, $param));
    }

    public function add()
    {
        $param = get_params();
        $id = (int) ($param['id'] ?? 0);
        if ($id === 0 && !$this->canApply()) {
            if (request()->isAjax()) {
                return to_assign(1, '当前部门不能使用转正申请');
            }
            throw new \think\exception\HttpException(403, '当前部门不能使用转正申请');
        }

        if (request()->isAjax()) {
            $profile = $this->applicantSnapshot();
            if (empty($profile)) {
                return to_assign(1, '申请人信息不存在或已停用');
            }
            if ((int) $profile['position_id'] < 1 || $profile['position_name'] === '') {
                return to_assign(1, '员工档案未设置岗位，请联系管理员完善后再提交');
            }

            $input = $this->normalizeInput($param);
            $data = array_merge($profile, $input);
            try {
                validate(EmployeeRegularizationValidate::class)->check($data);
            } catch (ValidateException $e) {
                return to_assign(1, $e->getError());
            }

            $startDate = strtotime($data['probation_start_date']);
            $endDate = strtotime($data['probation_end_date']);
            if ($startDate === false || $endDate === false) {
                return to_assign(1, '试用起止日期格式不正确');
            }
            if ($endDate < $startDate) {
                return to_assign(1, '试用结束日期不能早于开始日期');
            }
            $data['probation_start_date'] = $startDate;
            $data['probation_end_date'] = $endDate;

            if ($id > 0) {
                $detail = $this->model->getById($id);
                $error = $this->editableError($detail);
                if ($error !== '') {
                    return to_assign(1, $error);
                }
                $data['application_time'] = (int) $detail['application_time'];
                return $this->model->edit($data, $id, (int) $this->uid);
            }

            $existing = $this->model->findActiveByAdmin((int) $this->uid);
            if (!empty($existing)) {
                return to_assign(1, '已有进行中、已撤回或已通过的转正申请，请勿重复提交');
            }
            $data['application_time'] = time();
            return $this->model->add($data);
        }

        $formData = $this->applicantSnapshot();
        if (empty($formData)) {
            throw new \think\exception\HttpException(404, '申请人信息不存在或已停用');
        }
        $formData['application_time_text'] = date('Y-m-d H:i');
        $formData['entry_date_text'] = $this->dateText((int) $formData['entry_date']);
        $formData['probation_start_date_text'] = $formData['entry_date_text'];
        $formData['probation_end_date_text'] = $this->defaultProbationEndDate();

        if ($id > 0) {
            $detail = $this->model->getById($id);
            $error = $this->editableError($detail);
            if ($error !== '') {
                throw new \think\exception\HttpException(empty($detail) ? 404 : 403, $error);
            }
            $formData = $detail->toArray();
        }

        View::assign([
            'detail' => $formData,
            'is_edit' => $id > 0,
            'profile_error' => (int) $formData['position_id'] < 1 ? '员工档案未设置岗位，请联系管理员完善后再提交' : '',
        ]);
        if (is_mobile()) {
            return view('qiye@/approve/add_regularization');
        }
        return view();
    }

    public function view($id)
    {
        $detail = $this->model->getById((int) $id);
        if (empty($detail)) {
            throw new \think\exception\HttpException(404, '转正申请不存在或已删除');
        }
        if (!$this->canView($detail)) {
            throw new \think\exception\HttpException(403, '无权限查看该转正申请');
        }
        View::assign([
            'detail' => $detail,
            'create_user' => get_admin((int) $detail['admin_id']),
        ]);
        if (is_mobile()) {
            return view('qiye@/approve/view_regularization');
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

    private function applicantSnapshot(): array
    {
        $admin = Db::name('Admin')
            ->where(['id' => (int) $this->uid, 'delete_time' => 0])
            ->whereIn('status', [0, 1])
            ->find();
        if (empty($admin)) {
            return [];
        }

        $positionName = '';
        if ((int) $admin['position_id'] > 0) {
            $positionName = (string) (Db::name('Position')
                ->where('id', (int) $admin['position_id'])
                ->value('title') ?: '');
        }
        $professionalTitle = $this->basicUserTitle((int) $admin['position_name'], 1);
        $employeeGrade = $this->basicUserTitle((int) $admin['position_rank'], 2);

        return [
            'admin_id' => (int) $admin['id'],
            'did' => (int) $admin['did'],
            'applicant_name' => trim((string) $admin['name']),
            'gender' => [1 => '男', 2 => '女'][(int) $admin['sex']] ?? '',
            'birth_date' => $this->profileDateText($admin['birthday'] ?? ''),
            'graduate_school' => trim((string) ($admin['graduate_school'] ?? '')),
            'speciality' => trim((string) ($admin['speciality'] ?? '')),
            'highest_education' => trim((string) ($admin['education'] ?? '')),
            'professional_title' => $professionalTitle,
            'position_id' => (int) $admin['position_id'],
            'position_name' => $positionName,
            'employee_grade' => $employeeGrade,
            'entry_date' => (int) ($admin['entry_time'] ?? 0),
            'department_name' => (string) (Db::name('Department')
                ->where('id', (int) $admin['did'])
                ->value('title') ?: ''),
        ];
    }

    private function basicUserTitle(int $id, int $type): string
    {
        if ($id < 1) {
            return '';
        }
        return (string) (Db::name('BasicUser')
            ->where(['id' => $id, 'types' => $type])
            ->value('title') ?: '');
    }

    private function normalizeInput(array $param): array
    {
        $fields = ['probation_start_date', 'probation_end_date', 'work_summary', 'main_achievements'];
        $data = [];
        foreach ($fields as $field) {
            $data[$field] = isset($param[$field]) && is_scalar($param[$field])
                ? trim((string) $param[$field])
                : '';
        }
        return $data;
    }

    private function defaultProbationEndDate(): string
    {
        $endTime = (int) (Db::name('LaborContract')
            ->where(['admin_id' => (int) $this->uid, 'delete_time' => 0])
            ->where('trial_end_time', '>', 0)
            ->order('id desc')
            ->value('trial_end_time') ?: 0);
        return $this->dateText($endTime);
    }

    private function profileDateText($value): string
    {
        $value = trim((string) $value);
        if ($value === '') {
            return '';
        }
        if (ctype_digit($value) && (int) $value > 100000000) {
            return date('Y-m-d', (int) $value);
        }
        $timestamp = strtotime($value);
        return $timestamp === false ? $value : date('Y-m-d', $timestamp);
    }

    private function dateText(int $timestamp): string
    {
        return $timestamp > 0 ? date('Y-m-d', $timestamp) : '';
    }

    private function canApply(): bool
    {
        $did = (int) $this->did;
        $category = Db::name('FlowCate')
            ->where(['name' => 'employee_regularization', 'status' => 1, 'is_list' => 1])
            ->where(function ($query) use ($did) {
                $query->where('department_ids', '')
                    ->whereOrRaw("FIND_IN_SET('{$did}',department_ids)");
            })
            ->find();
        if (empty($category)) {
            return false;
        }
        return Db::name('Flow')
            ->where(['cate_id' => (int) $category['id'], 'status' => 1, 'delete_time' => 0])
            ->where(function ($query) use ($did) {
                $query->where('department_ids', '')
                    ->whereOrRaw("FIND_IN_SET('{$did}',department_ids)");
            })
            ->count() > 0;
    }

    private function editableError($detail): string
    {
        if (empty($detail)) {
            return '转正申请不存在或已删除';
        }
        if ((int) $detail['admin_id'] !== (int) $this->uid) {
            return '无权限编辑他人的转正申请';
        }
        if (!in_array((int) $detail['check_status'], [0, 4], true)) {
            return '当前审批状态不允许编辑或删除';
        }
        return '';
    }

    private function canView($detail): bool
    {
        if ((int) $this->uid === 1 || (int) $detail['admin_id'] === (int) $this->uid || $this->isPersonnelAdmin()) {
            return true;
        }
        if (in_array((int) $detail['did'], $this->managedDepartmentIds(), true)) {
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

    private function isPersonnelAdmin(): bool
    {
        return (int) $this->uid === 1 || isAuth($this->uid, 'office_admin', 'conf_1') === 1;
    }

    private function managedDepartmentIds(): array
    {
        $ids = array_merge(get_leader_departments((int) $this->uid), get_role_departments((int) $this->uid));
        return array_values(array_unique(array_map('intval', $ids)));
    }
}
