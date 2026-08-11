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
            $where[] = ['admin_id', '=', $this->uid];
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
                $detail = $this->model->getById((int) $param['id']);
                $error = $this->editableError($detail);
                if ($error !== '') {
                    return to_assign(1, $error);
                }
                return $this->model->edit($param, $this->uid);
            } else {
                $param['admin_id'] = $this->uid;
                $param['did'] = $this->did;
                return $this->model->add($param);
            }
        } else {
            $id = isset($param['id']) ? (int) $param['id'] : 0;
            if ($id > 0) {
                $detail = $this->model->getById($id);
                $error = $this->editableError($detail);
                if ($error !== '') {
                    $code = empty($detail) ? 404 : 403;
                    throw new \think\exception\HttpException($code, $error);
                }
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
        $detail = $this->model->getById((int) $id);
        if (!empty($detail)) {
            if (!$this->canView($detail)) {
                throw new \think\exception\HttpException(403, '无权限查看该补卡申请');
            }
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
        $id = isset($param['id']) ? (int) $param['id'] : 0;
        if (request()->isDelete()) {
            $detail = $this->model->getById($id);
            $error = $this->editableError($detail);
            if ($error !== '') {
                return to_assign(1, $error);
            }
            return $this->model->delById($id, $this->uid);
        } else {
            return to_assign(1, "错误的请求");
        }
    }

    private function editableError($detail): string
    {
        if (empty($detail)) {
            return '补卡申请不存在或已删除';
        }
        if ((int) $detail['admin_id'] !== (int) $this->uid) {
            return '无权限编辑他人的补卡申请';
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
