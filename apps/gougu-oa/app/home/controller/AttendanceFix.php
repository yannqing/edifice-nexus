<?php
declare (strict_types = 1);

namespace app\home\controller;

use app\base\BaseController;
use app\home\model\AttendanceFix as AttendanceFixModel;
use think\facade\Db;
use think\facade\View;

class AttendanceFix extends BaseController
{
    protected $model;

    public function __construct()
    {
        parent::__construct();
        $this->model = new AttendanceFixModel();
    }

    /**
     * 数据列表
     */
    public function datalist()
    {
        $param = get_params();
        if (request()->isAjax()) {
            $where = [];
            $where[] = ['delete_time', '=', 0];
            if (!empty($param['keywords'])) {
                $where[] = ['id|reason', 'like', '%' . $param['keywords'] . '%'];
            }
            $list = $this->model->datalist($where, $param);
            return table_assign(0, '', $list);
        } else {
            return view();
        }
    }

    /**
     * 添加/编辑
     */
    public function add()
    {
        $param = get_params();
        if (request()->isAjax()) {
            if (empty($param['fix_date'])) {
                return to_assign(1, '请选择补卡日期');
            }
            $param['fix_date'] = strtotime($param['fix_date']);
            if (empty($param['fix_time'])) {
                return to_assign(1, '请选择补卡时间');
            }
            if (empty($param['reason'])) {
                return to_assign(1, '请填写补卡原因');
            }
            if (!empty($param['id']) && $param['id'] > 0) {
                $this->model->edit($param);
            } else {
                $param['admin_id'] = $this->uid;
                $param['did'] = $this->did;
                $this->model->add($param);
            }
        } else {
            $id = isset($param['id']) ? $param['id'] : 0;
            if ($id > 0) {
                $detail = $this->model->getById($id);
                View::assign('detail', $detail);
            }
            return view();
        }
    }

    /**
     * 查看
     */
    public function view($id)
    {
        $detail = $this->model->getById($id);
        if (!empty($detail)) {
            View::assign('create_user', get_admin($detail['admin_id']));
            $detail['fix_type_name'] = $detail['fix_type'] == 1 ? '上班补卡' : '下班补卡';
            View::assign('detail', $detail);
            return view();
        } else {
            return view(EEEOR_REPORTING, ['code' => 404, 'warning' => '找不到页面']);
        }
    }

    /**
     * 删除
     */
    public function del()
    {
        $param = get_params();
        $id = $param['id'];
        if (request()->isDelete()) {
            $this->model->delById($id);
        } else {
            return to_assign(1, "错误的请求");
        }
    }
}
