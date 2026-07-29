<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\qiye\BaseController;
use think\facade\Db;
use think\facade\View;

class Finance extends BaseController
{
    private const TYPES = [
        'expense' => [
            'title' => '报销管理',
            'table' => 'Expense',
            'name_field' => 'code',
            'amount_field' => 'cost',
            'check_name' => 'expense',
        ],
        'invoice' => [
            'title' => '开票管理',
            'table' => 'Invoice',
            'name_field' => 'invoice_title',
            'amount_field' => 'amount',
            'check_name' => 'invoice',
        ],
        'ticket' => [
            'title' => '收票管理',
            'table' => 'Ticket',
            'name_field' => 'invoice_title',
            'amount_field' => 'amount',
            'check_name' => 'ticket',
        ],
        'income' => [
            'title' => '回款管理',
            'table' => 'Invoice',
            'name_field' => 'invoice_title',
            'amount_field' => 'enter_amount',
            'check_name' => 'invoice',
            'settlement' => 'income',
        ],
        'payment' => [
            'title' => '付款管理',
            'table' => 'Ticket',
            'name_field' => 'invoice_title',
            'amount_field' => 'pay_amount',
            'check_name' => 'ticket',
            'settlement' => 'payment',
        ],
        'loan' => [
            'title' => '借支管理',
            'table' => 'Loan',
            'name_field' => 'title',
            'amount_field' => 'cost',
            'check_name' => 'loan',
        ],
    ];

    public function expense()
    {
        return $this->renderType('expense');
    }

    public function invoice()
    {
        return $this->renderType('invoice');
    }

    public function ticket()
    {
        return $this->renderType('ticket');
    }

    public function income()
    {
        return $this->renderType('income');
    }

    public function payment()
    {
        return $this->renderType('payment');
    }

    public function loan()
    {
        return $this->renderType('loan');
    }

    public function view()
    {
        $type = (string) request()->param('type', '');
        $config = self::TYPES[$type] ?? null;
        $id = (int) request()->param('id', 0);
        if ($config === null || $id < 1) {
            throw new \think\exception\HttpException(404, '找不到财务记录');
        }

        $detail = $this->visibleQuery($config)
            ->where('id', $id)
            ->find();
        if (empty($detail)) {
            throw new \think\exception\HttpException(403, '无权查看该财务记录');
        }

        if ($type === 'invoice' && (int) $detail['invoice_type'] === 0) {
            $config['check_name'] = 'invoicea';
        } elseif ($type === 'ticket' && (int) $detail['invoice_type'] === 0) {
            $config['check_name'] = 'ticketa';
        }

        $fields = [
            ['label' => '名称', 'value' => $detail[$config['name_field']] ?: ('#' . $detail['id'])],
            ['label' => '金额', 'value' => '¥' . number_format((float) $detail[$config['amount_field']], 2)],
            ['label' => '申请人', 'value' => Db::name('Admin')->where('id', $detail['admin_id'])->value('name') ?: '-'],
            ['label' => '所属部门', 'value' => Db::name('Department')->where('id', $detail['did'])->value('title') ?: '-'],
            ['label' => '创建时间', 'value' => to_date((int) $detail['create_time'], 'Y-m-d H:i')],
        ];
        if (array_key_exists('check_status', $detail)) {
            $fields[] = ['label' => '审批状态', 'value' => check_status_name((int) $detail['check_status'])];
        }
        foreach (['content' => '内容', 'remark' => '备注'] as $field => $label) {
            if (!empty($detail[$field])) {
                $fields[] = ['label' => $label, 'value' => strip_tags((string) $detail[$field])];
            }
        }

        View::assign([
            'page_title' => $config['title'] . '详情',
            'finance_config' => $config,
            'detail' => $detail,
            'detail_fields' => $fields,
        ]);
        return view('view');
    }

    private function renderType(string $type)
    {
        $config = self::TYPES[$type];
        if (request()->isAjax()) {
            $query = $this->visibleQuery($config);
            $status = (int) request()->param('status', 0);
            if ($status === 1) {
                $query->where('check_status', '<', 2);
            } elseif ($status === 2) {
                $query->where('check_status', 2);
            } elseif ($status === 3) {
                $query->where('check_status', '>', 2);
            }
            $pageSize = min(50, max(1, (int) request()->param('limit', 15)));
            $list = $query
                ->order('id desc')
                ->paginate(['list_rows' => $pageSize])
                ->each(function ($item) use ($config, $type) {
                    $item->mobile_title = $item[$config['name_field']] ?: ('#' . $item->id);
                    $item->mobile_amount = number_format((float) $item[$config['amount_field']], 2);
                    $item->mobile_admin = Db::name('Admin')->where('id', $item->admin_id)->value('name') ?: '-';
                    $item->mobile_time = to_date((int) $item->create_time, 'Y-m-d H:i');
                    $item->mobile_status = check_status_name((int) $item->check_status);
                    $item->mobile_url = '/qiye/finance/view?' . http_build_query([
                        'type' => $type,
                        'id' => $item->id,
                    ]);
                });
            return table_assign(0, '', $list);
        }

        View::assign([
            'page_title' => $config['title'],
            'finance_type' => $type,
            'finance_is_settlement' => isset($config['settlement']),
        ]);
        return view('list');
    }

    private function visibleQuery(array $config)
    {
        $query = Db::name($config['table'])->where('delete_time', 0);
        if (($config['settlement'] ?? '') === 'income') {
            $query->where([
                'check_status' => 2,
                'open_status' => 1,
            ])->where('invoice_type', '>', 0);
        } elseif (($config['settlement'] ?? '') === 'payment') {
            $query->where([
                'check_status' => 2,
                'open_status' => 1,
            ])->where('invoice_type', '>', 0);
        }

        if ($this->uid > 1) {
            $uid = $this->uid;
            $visibleDids = array_unique(array_merge(
                get_leader_departments($uid),
                get_role_departments($uid)
            ));
            $query->where(function ($where) use ($uid, $visibleDids) {
                $where->where('admin_id', $uid)
                    ->whereOrRaw("FIND_IN_SET('{$uid}',check_uids)")
                    ->whereOrRaw("FIND_IN_SET('{$uid}',check_history_uids)")
                    ->whereOrRaw("FIND_IN_SET('{$uid}',check_copy_uids)");
                if (!empty($visibleDids)) {
                    $where->whereOr('did', 'in', $visibleDids);
                }
            });
        }
        return $query;
    }
}
