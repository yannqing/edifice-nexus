<?php

declare(strict_types=1);

namespace app\adm\model;

use think\facade\Db;
use think\Model;

class CarApply extends Model
{
    protected $name = 'car_apply';

    public function datalist(array $where, array $whereOr, array $param)
    {
        $rows = empty($param['limit']) ? (int) get_config('app.page_size') : (int) $param['limit'];
        $rows = min(100, max(1, $rows));
        $orderFields = [
            'id',
            'create_time',
            'use_start_time',
            'use_end_time',
            'check_status',
            'passenger_count',
        ];
        $order = 'id desc';
        if (!empty($param['order']) && preg_match('/^([a-z_]+)\s+(asc|desc)$/i', trim((string) $param['order']), $matches)) {
            if (in_array(strtolower($matches[1]), $orderFields, true)) {
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
                    $item->admin_name = Db::name('Admin')->where('id', $item->admin_id)->value('name') ?: '-';
                    $item->department = Db::name('Department')->where('id', $item->did)->value('title') ?: '-';
                    $item->use_time = date('Y-m-d H:i', (int) $item->use_start_time)
                        . ' 至 ' . date('Y-m-d H:i', (int) $item->use_end_time);
                    $item->check_status_str = check_status_name((int) $item->check_status);
                    $item->check_user = '-';
                    if ((int) $item->check_status === 1 && !empty($item->check_uids)) {
                        $names = Db::name('Admin')->whereIn('id', $item->check_uids)->column('name');
                        $item->check_user = implode(',', $names);
                    }
                    $item->create_time = to_date((int) $item->create_time);
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
            add_log('add', $insertId, $data, '用车申请');
            return to_assign(0, '操作成功', ['return_id' => $insertId]);
        } catch (\Throwable $e) {
            return to_assign(1, '操作失败，原因：' . $e->getMessage());
        }
    }

    public function edit(array $param, int $adminId)
    {
        try {
            $data = $this->writableData($param, false);
            $data['update_time'] = time();
            self::where('id', (int) $param['id'])
                ->where('admin_id', $adminId)
                ->whereIn('check_status', [0, 4])
                ->strict(false)
                ->field(true)
                ->update($data);
            add_log('edit', (int) $param['id'], $data, '用车申请');
            return to_assign(0, '操作成功', ['return_id' => (int) $param['id']]);
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
        $info['use_start_time_text'] = date('Y-m-d H:i', (int) $info['use_start_time']);
        $info['use_end_time_text'] = date('Y-m-d H:i', (int) $info['use_end_time']);
        $info['admin_name'] = Db::name('Admin')->where('id', $info['admin_id'])->value('name') ?: '-';
        $info['department'] = Db::name('Department')->where('id', $info['did'])->value('title') ?: '-';
        if (!empty($info['file_ids'])) {
            $info['file_array'] = Db::name('File')->whereIn('id', $info['file_ids'])->select();
        }
        return $info;
    }

    public function delById(int $id, int $adminId)
    {
        try {
            self::where('id', $id)
                ->where('admin_id', $adminId)
                ->whereIn('check_status', [0, 4])
                ->update(['delete_time' => time()]);
            add_log('delete', $id, [], '用车申请');
            return to_assign();
        } catch (\Throwable $e) {
            return to_assign(1, '操作失败，原因：' . $e->getMessage());
        }
    }

    private function writableData(array $param, bool $includeAdmin): array
    {
        $fields = [
            'did',
            'start_address',
            'destination',
            'use_start_time',
            'use_end_time',
            'contact_name',
            'contact_phone',
            'budget_item',
            'passenger_count',
            'business_type',
            'description',
            'file_ids',
        ];
        if ($includeAdmin) {
            $fields[] = 'admin_id';
        }
        return array_intersect_key($param, array_flip($fields));
    }
}
