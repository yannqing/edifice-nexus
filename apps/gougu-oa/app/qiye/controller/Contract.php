<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\contract\model\Contract as ContractModel;
use app\contract\model\Purchase as PurchaseModel;
use app\qiye\BaseController;
use think\facade\Db;
use think\facade\View;

class Contract extends BaseController
{
    public function index()
    {
        View::assign([
            'page_title' => '合同',
            'contract_summary' => [
                'sales' => $this->contractCount('Contract'),
                'purchase' => $this->contractCount('Purchase'),
                'pending' => $this->pendingCount(),
            ],
        ]);
        return view();
    }

    public function datalist()
    {
        $kind = request()->param('kind', 'sales') === 'purchase' ? 'purchase' : 'sales';
        $tab = (int) request()->param('tab', 0);
        if (request()->isAjax()) {
            [$where, $whereOr] = $this->contractWhere($tab);
            $keywords = trim((string) request()->param('keywords', ''));
            if ($keywords !== '') {
                $where[] = ['id|name|code', 'like', '%' . $keywords . '%'];
            }
            $model = $kind === 'purchase' ? new PurchaseModel() : new ContractModel();
            return table_assign(0, '', $model->datalist($this->paginationParams(), $where, $whereOr));
        }

        View::assign([
            'page_title' => $kind === 'purchase' ? '采购合同' : '销售合同',
            'contract_kind' => $kind,
            'contract_tab' => $tab,
        ]);
        return view('list');
    }

    public function view()
    {
        $kind = request()->param('kind', 'sales') === 'purchase' ? 'purchase' : 'sales';
        $id = (int) request()->param('id', 0);
        $table = $kind === 'purchase' ? 'Purchase' : 'Contract';
        $detail = Db::name($table)->where(['id' => $id, 'delete_time' => 0])->find();
        if (empty($detail) || !$this->canView($detail)) {
            throw new \think\exception\HttpException(403, '无权查看该合同');
        }

        $detail['sign_name'] = Db::name('Admin')->where('id', $detail['sign_uid'])->value('name') ?: '-';
        $detail['status_name'] = check_status_name((int) $detail['check_status']);
        View::assign([
            'page_title' => '合同详情',
            'contract_kind' => $kind,
            'contract_check_name' => $kind === 'purchase' ? 'purchase' : 'contract',
            'detail' => $detail,
        ]);
        return view();
    }

    private function contractCount(string $table): int
    {
        [$where, $whereOr] = $this->contractWhere(0);
        return (int) Db::name($table)
            ->where($where)
            ->where(function ($query) use ($whereOr) {
                if (!empty($whereOr)) {
                    $query->whereOr($whereOr);
                }
            })
            ->count();
    }

    private function pendingCount(): int
    {
        $uid = $this->uid;
        return (int) (
            Db::name('Contract')
                ->where(['delete_time' => 0, 'archive_time' => 0, 'stop_time' => 0, 'void_time' => 0])
                ->whereRaw("FIND_IN_SET('{$uid}',check_uids)")
                ->count()
            + Db::name('Purchase')
                ->where(['delete_time' => 0, 'archive_time' => 0, 'stop_time' => 0, 'void_time' => 0])
                ->whereRaw("FIND_IN_SET('{$uid}',check_uids)")
                ->count()
        );
    }

    private function contractWhere(int $tab): array
    {
        $where = [
            ['delete_time', '=', 0],
            ['archive_time', '=', 0],
            ['stop_time', '=', 0],
            ['void_time', '=', 0],
        ];
        $whereOr = [];
        if ($tab === 1) {
            $where[] = ['sign_uid', '=', $this->uid];
        } elseif ($tab === 2) {
            $where[] = ['', 'exp', Db::raw("FIND_IN_SET('{$this->uid}',check_uids)")];
        } elseif ($this->uid > 1 && isAuth($this->uid, 'contract_admin', 'conf_1') === 0) {
            $whereOr[] = ['admin_id|prepared_uid|sign_uid|keeper_uid', '=', $this->uid];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$this->uid}',share_ids)")];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$this->uid}',check_uids)")];
            $whereOr[] = ['', 'exp', Db::raw("FIND_IN_SET('{$this->uid}',check_history_uids)")];
            $dids = array_unique(array_merge(
                get_leader_departments($this->uid),
                get_role_departments($this->uid)
            ));
            if (!empty($dids)) {
                $whereOr[] = ['did', 'in', $dids];
            }
        }
        return [$where, $whereOr];
    }

    private function canView(array $detail): bool
    {
        if ($this->uid === 1 || isAuth($this->uid, 'contract_admin', 'conf_1') === 1) {
            return true;
        }
        foreach (['admin_id', 'prepared_uid', 'sign_uid', 'keeper_uid'] as $field) {
            if ((int) $detail[$field] === $this->uid) {
                return true;
            }
        }
        foreach (['share_ids', 'check_uids', 'check_history_uids'] as $field) {
            $ids = array_filter(array_map('intval', explode(',', (string) $detail[$field])));
            if (in_array($this->uid, $ids, true)) {
                return true;
            }
        }
        $visibleDids = array_unique(array_merge(
            get_leader_departments($this->uid),
            get_role_departments($this->uid)
        ));
        return in_array((int) $detail['did'], $visibleDids, true);
    }
}
