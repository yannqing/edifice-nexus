<?php

declare(strict_types=1);

namespace app\user\model;

use think\facade\Db;
use think\Model;

class EmployeeRegularization extends Model
{
    protected $name = 'employee_regularization';

    public function datalist(array $where, array $whereOr, array $param)
    {
        $rows = empty($param['limit']) ? (int) get_config('app.page_size') : (int) $param['limit'];
        $rows = min(100, max(1, $rows));
        $order = 'id desc';
        if (!empty($param['order']) && preg_match('/^([a-z_]+)\s+(asc|desc)$/i', trim((string) $param['order']), $matches)) {
            $allowed = ['id', 'application_time', 'probation_start_date', 'probation_end_date', 'check_status'];
            if (in_array(strtolower($matches[1]), $allowed, true)) {
                $order = strtolower($matches[1]) . ' ' . strtolower($matches[2]);
            }
        }

        try {
            return self::where($where)
                ->where(function ($query) use ($whereOr) {
                    if (!empty($whereOr)) {
                        $query->whereOr($whereOr);
                    }
                })
                ->order($order)
                ->paginate(['list_rows' => $rows])
                ->each(function ($item) {
                    $item->check_status_str = check_status_name((int) $item->check_status);
                    $item->check_user = '-';
                    if ((int) $item->check_status === 1 && !empty($item->check_uids)) {
                        $names = Db::name('Admin')->whereIn('id', $item->check_uids)->column('name');
                        $item->check_user = implode(',', $names);
                    }
                    $item->probation_period = $this->dateText((int) $item->probation_start_date)
                        . ' 至 ' . $this->dateText((int) $item->probation_end_date);
                    $item->application_time = to_date((int) $item->application_time, 'Y-m-d H:i');
                });
        } catch (\Throwable $e) {
            return ['code' => 1, 'data' => [], 'msg' => $e->getMessage()];
        }
    }

    public function add(array $param)
    {
        try {
            $data = $this->writableData($param, true);
            $data['create_time'] = time();
            $insertId = self::strict(false)->field(true)->insertGetId($data);
            add_log('add', $insertId, $data, '转正申请');
            return to_assign(0, '操作成功', ['return_id' => $insertId]);
        } catch (\Throwable $e) {
            return to_assign(1, '操作失败，原因：' . $e->getMessage());
        }
    }

    public function edit(array $param, int $id, int $adminId)
    {
        try {
            $data = $this->writableData($param, true);
            $data['update_time'] = time();
            self::where('id', $id)
                ->where('admin_id', $adminId)
                ->whereIn('check_status', [0, 4])
                ->strict(false)
                ->field(true)
                ->update($data);
            add_log('edit', $id, $data, '转正申请');
            return to_assign(0, '操作成功', ['return_id' => $id]);
        } catch (\Throwable $e) {
            return to_assign(1, '操作失败，原因：' . $e->getMessage());
        }
    }

    public function getById(int $id)
    {
        $info = self::where('id', $id)->where('delete_time', 0)->find();
        if (empty($info)) {
            return null;
        }
        $info['application_time_text'] = to_date((int) $info['application_time'], 'Y-m-d H:i');
        $info['entry_date_text'] = $this->dateText((int) $info['entry_date']);
        $info['probation_start_date_text'] = $this->dateText((int) $info['probation_start_date']);
        $info['probation_end_date_text'] = $this->dateText((int) $info['probation_end_date']);
        return $info;
    }

    public function findActiveByAdmin(int $adminId)
    {
        return self::where(['admin_id' => $adminId, 'delete_time' => 0])
            ->whereIn('check_status', [0, 1, 2, 4])
            ->order('id desc')
            ->find();
    }

    public function delById(int $id, int $adminId)
    {
        try {
            self::where('id', $id)
                ->where('admin_id', $adminId)
                ->whereIn('check_status', [0, 4])
                ->update(['delete_time' => time()]);
            add_log('delete', $id, [], '转正申请');
            return to_assign();
        } catch (\Throwable $e) {
            return to_assign(1, '操作失败，原因：' . $e->getMessage());
        }
    }

    private function writableData(array $param, bool $includeApplicant): array
    {
        $fields = [
            'probation_start_date',
            'probation_end_date',
            'work_summary',
            'main_achievements',
        ];
        if ($includeApplicant) {
            $fields = array_merge($fields, [
                'admin_id',
                'did',
                'applicant_name',
                'application_time',
                'gender',
                'birth_date',
                'graduate_school',
                'speciality',
                'highest_education',
                'professional_title',
                'position_id',
                'position_name',
                'employee_grade',
                'entry_date',
                'department_name',
            ]);
        }
        return array_intersect_key($param, array_flip($fields));
    }

    private function dateText(int $timestamp): string
    {
        return $timestamp > 0 ? date('Y-m-d', $timestamp) : '-';
    }
}
