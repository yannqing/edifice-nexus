<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\home\model\Msg as MsgModel;
use app\qiye\BaseController;
use think\facade\Db;
use think\facade\View;

class Msg extends BaseController
{
    private MsgModel $model;

    public function __construct()
    {
        parent::__construct();
        $this->model = new MsgModel();
    }

    public function index()
    {
        if (request()->isAjax()) {
            $status = (int) request()->param('status', 0);
            $where = [
                ['to_uid', '=', $this->uid],
                ['delete_time', '=', 0],
            ];
            if ($status === 1) {
                $where[] = ['read_time', '=', 0];
            } elseif ($status === 2) {
                $where[] = ['read_time', '>', 0];
            }
            $list = $this->model->datalist($where, $this->paginationParams());
            return table_assign(0, '', $list);
        }

        View::assign([
            'page_title' => '消息',
            'unread_count' => Db::name('Msg')
                ->where(['to_uid' => $this->uid, 'read_time' => 0, 'delete_time' => 0])
                ->count(),
        ]);
        return view();
    }

    public function read(int $id)
    {
        $detail = $this->model->detail($id);
        if (empty($detail) || (int) $detail['to_uid'] !== $this->uid) {
            throw new \think\exception\HttpException(404, '找不到消息');
        }

        Db::name('Msg')->where('id', $id)->update(['read_time' => time()]);
        $detail['from_name'] = '系统';
        if ((int) $detail['from_uid'] > 0) {
            $detail['from_name'] = Db::name('Admin')->where('id', $detail['from_uid'])->value('name') ?: '未知用户';
        }
        View::assign([
            'page_title' => '消息详情',
            'detail' => $detail,
        ]);
        return view();
    }
}
