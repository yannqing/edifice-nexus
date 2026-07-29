<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\customer\model\Customer as CustomerModel;
use app\qiye\BaseController;
use think\facade\Db;
use think\facade\View;

class Customer extends BaseController
{
    private CustomerModel $model;

    public function __construct()
    {
        parent::__construct();
        $this->model = new CustomerModel();
    }

    public function index()
    {
        $leaderDids = get_leader_departments($this->uid);
        $base = Db::name('Customer')->where(['delete_time' => 0, 'discard_time' => 0]);
        $counts = [
            'mine' => (clone $base)->where('belong_uid', $this->uid)->count(),
            'shared' => (clone $base)->whereRaw("FIND_IN_SET('{$this->uid}',share_ids)")->count(),
            'subordinate' => empty($leaderDids)
                ? 0
                : (clone $base)
                    ->where('belong_uid', '<>', $this->uid)
                    ->whereIn('belong_did', $leaderDids)
                    ->count(),
            'sea' => (clone $base)->where('belong_uid', 0)->count(),
        ];
        View::assign([
            'page_title' => '客户',
            'customer_counts' => $counts,
        ]);
        return view();
    }

    public function datalist()
    {
        $tab = (int) request()->param('tab', 0);
        if (request()->isAjax()) {
            [$where, $whereOr] = $this->customerWhere($tab);
            $keywords = trim((string) request()->param('keywords', ''));
            if ($keywords !== '') {
                $where[] = ['id|name', 'like', '%' . $keywords . '%'];
            }
            $list = $this->model->datalist($this->paginationParams(), $where, $whereOr);
            return table_assign(0, '', $list);
        }

        $titles = [0 => '全部客户', 1 => '我的客户', 2 => '下属客户', 3 => '共享客户', 4 => '公海客户'];
        View::assign([
            'page_title' => $titles[$tab] ?? '客户列表',
            'customer_tab' => $tab,
        ]);
        return view('list');
    }

    public function view(int $id)
    {
        $customer = Db::name('Customer')
            ->where(['id' => $id, 'delete_time' => 0, 'discard_time' => 0])
            ->find();
        if (empty($customer) || !$this->canView($customer)) {
            throw new \think\exception\HttpException(403, '无权查看该客户');
        }

        $detail = $this->model->getById($id);
        $detail['contact'] = Db::name('CustomerContact')
            ->where(['cid' => $id, 'is_default' => 1, 'delete_time' => 0])
            ->find();
        View::assign([
            'page_title' => '客户详情',
            'detail' => $detail,
        ]);
        return view();
    }

    public function contacts()
    {
        $customerIds = $this->visibleCustomerIds();
        $rows = empty($customerIds) ? [] : Db::name('CustomerContact')
            ->where('delete_time', 0)
            ->whereIn('cid', $customerIds)
            ->order('id desc')
            ->limit(100)
            ->select()
            ->toArray();
        $items = [];
        foreach ($rows as $row) {
            $items[] = [
                'title' => (string) $row['name'],
                'meta' => Db::name('Customer')->where('id', $row['cid'])->value('name') ?: '-',
                'summary' => trim((string) $row['position'] . ' ' . (string) $row['mobile']),
                'phone' => (string) $row['mobile'],
            ];
        }
        return $this->renderRelated('客户联系人', $items);
    }

    public function chances()
    {
        $customerIds = $this->visibleCustomerIds();
        $rows = empty($customerIds) ? [] : Db::name('CustomerChance')
            ->where('delete_time', 0)
            ->whereIn('cid', $customerIds)
            ->order('id desc')
            ->limit(100)
            ->select()
            ->toArray();
        $items = [];
        foreach ($rows as $row) {
            $items[] = [
                'title' => (string) $row['title'],
                'meta' => Db::name('Customer')->where('id', $row['cid'])->value('name') ?: '-',
                'summary' => '预计金额 ¥' . number_format((float) $row['expected_amount'], 2),
                'phone' => '',
            ];
        }
        return $this->renderRelated('销售机会', $items);
    }

    public function traces()
    {
        $customerIds = $this->visibleCustomerIds();
        $rows = empty($customerIds) ? [] : Db::name('CustomerTrace')
            ->where('delete_time', 0)
            ->whereIn('cid', $customerIds)
            ->order('follow_time desc,id desc')
            ->limit(100)
            ->select()
            ->toArray();
        $items = [];
        foreach ($rows as $row) {
            $items[] = [
                'title' => Db::name('Customer')->where('id', $row['cid'])->value('name') ?: '客户跟进',
                'meta' => to_date((int) $row['follow_time'], 'Y-m-d H:i'),
                'summary' => strip_tags((string) $row['content']),
                'phone' => '',
            ];
        }
        return $this->renderRelated('跟进记录', $items);
    }

    private function customerWhere(int $tab): array
    {
        $where = [
            ['delete_time', '=', 0],
            ['discard_time', '=', 0],
        ];
        $whereOr = [];
        if ($tab === 1) {
            $where[] = ['belong_uid', '=', $this->uid];
        } elseif ($tab === 2) {
            $dids = get_leader_departments($this->uid);
            $where[] = ['belong_uid', '<>', $this->uid];
            $where[] = ['belong_did', 'in', empty($dids) ? [-1] : $dids];
        } elseif ($tab === 3) {
            $where[] = ['', 'exp', Db::raw("FIND_IN_SET('{$this->uid}',share_ids)")];
        } elseif ($tab === 4) {
            $where[] = ['belong_uid', '=', 0];
        } elseif ($this->uid > 1 && isAuth($this->uid, 'customer_admin', 'conf_1') === 0) {
            $whereOr[] = ['belong_uid', '=', $this->uid];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$this->uid}',share_ids)")];
            $dids = array_unique(array_merge(
                get_leader_departments($this->uid),
                get_role_departments($this->uid)
            ));
            if (!empty($dids)) {
                $whereOr[] = ['belong_did', 'in', $dids];
            }
        }
        return [$where, $whereOr];
    }

    private function canView(array $customer): bool
    {
        if ($this->uid === 1 || (int) $customer['belong_uid'] === 0) {
            return true;
        }
        if ((int) $customer['belong_uid'] === $this->uid) {
            return true;
        }
        $shareIds = array_filter(array_map('intval', explode(',', (string) $customer['share_ids'])));
        if (in_array($this->uid, $shareIds, true)) {
            return true;
        }
        $visibleDids = array_unique(array_merge(
            get_leader_departments($this->uid),
            get_role_departments($this->uid)
        ));
        return in_array((int) $customer['belong_did'], $visibleDids, true);
    }

    private function visibleCustomerIds(): array
    {
        [$where, $whereOr] = $this->customerWhere(0);
        return Db::name('Customer')
            ->where($where)
            ->where(function ($query) use ($whereOr) {
                if (!empty($whereOr)) {
                    $query->whereOr($whereOr);
                }
            })
            ->column('id');
    }

    private function renderRelated(string $title, array $items)
    {
        View::assign([
            'page_title' => $title,
            'related_items' => $items,
        ]);
        return view('related');
    }
}
