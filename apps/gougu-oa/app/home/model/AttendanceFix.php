<?php
namespace app\home\model;

use think\model;
use think\facade\Db;

class AttendanceFix extends Model
{
    protected $name = 'attendance_fix';

    /**
     * 获取分页列表
     */
    public function datalist($where, $param)
    {
        $rows = empty($param['limit']) ? get_config('app.page_size') : $param['limit'];
        $order = empty($param['order']) ? 'id desc' : $param['order'];
        try {
            $list = self::where($where)
                ->order($order)
                ->paginate(['list_rows' => $rows])
                ->each(function ($item, $key) {
                    $item->admin_name = Db::name('Admin')->where('id', $item->admin_id)->value('name');
                    $item->fix_date_str = date('Y-m-d', $item->fix_date);
                    $item->fix_type_name = $item->fix_type == 1 ? '上班补卡' : '下班补卡';
                    $item->check_status_name = check_status_name($item->check_status);
                });
            return $list;
        } catch (\Exception $e) {
            return ['code' => 1, 'data' => [], 'msg' => $e->getMessage()];
        }
    }

    /**
     * 添加数据
     */
    public function add($param)
    {
        $insertId = 0;
        try {
            $param['create_time'] = time();
            $insertId = self::strict(false)->field(true)->insertGetId($param);
            add_log('add', $insertId, $param, '补卡申请');
        } catch (\Exception $e) {
            return to_assign(1, '操作失败，原因：' . $e->getMessage());
        }
        return to_assign(0, '操作成功', ['return_id' => $insertId]);
    }

    /**
     * 编辑信息
     */
    public function edit($param)
    {
        try {
            $param['update_time'] = time();
            self::where('id', $param['id'])->strict(false)->field(true)->update($param);
            add_log('edit', $param['id'], $param, '补卡申请');
        } catch (\Exception $e) {
            return to_assign(1, '操作失败，原因：' . $e->getMessage());
        }
        return to_assign(0, '操作成功', ['return_id' => $param['id']]);
    }

    /**
     * 根据id获取信息
     */
    public function getById($id)
    {
        $info = self::find($id);
        if (empty($info)) return null;
        $info['fix_date_str'] = date('Y-m-d', $info['fix_date']);
        $info['admin_name'] = Db::name('Admin')->where('id', '=', $info['admin_id'])->value('name');
        $info['department'] = Db::name('Department')->where('id', '=', $info['did'])->value('title');
        if (!empty($info['file_ids'])) {
            $file_array = Db::name('File')->where('id', 'in', $info['file_ids'])->select();
            $info['file_array'] = $file_array;
        }
        return $info;
    }

    /**
     * 删除信息
     */
    public function delById($id, $type = 0)
    {
        if ($type == 0) {
            try {
                self::where('id', $id)->update(['delete_time' => time()]);
                add_log('delete', $id);
            } catch (\Exception $e) {
                return to_assign(1, '操作失败，原因：' . $e->getMessage());
            }
        } else {
            try {
                self::destroy($id);
                add_log('delete', $id);
            } catch (\Exception $e) {
                return to_assign(1, '操作失败，原因：' . $e->getMessage());
            }
        }
        return to_assign();
    }
}
