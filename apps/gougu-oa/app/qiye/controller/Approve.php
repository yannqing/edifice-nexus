<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\qiye\BaseController;
use think\facade\Db;
use think\facade\View;

class Approve extends BaseController
{
    private const MOBILE_APPLY_ROUTES = [
        'leaves' => '/qiye/approve/add_qingjia',
        'trips' => '/qiye/approve/add_chuchai',
        'outs' => '/qiye/approve/add_waichu',
        'overtimes' => '/qiye/approve/add_jiaban',
    ];

    public function apply()
    {
        $department = $this->did;
        $whereOr = [];
        if ($this->uid > 1) {
            $whereOr = [
                [['department_ids', '=', '']],
                [['', 'exp', Db::raw("FIND_IN_SET('{$department}',department_ids)")]],
            ];
        }

        $availableFlowCateIds = Db::name('Flow')
            ->where([
                ['status', '=', 1],
                ['delete_time', '=', 0],
            ])
            ->where(function ($query) use ($department) {
                $query->where('department_ids', '')
                    ->whereOrRaw("FIND_IN_SET('{$department}',department_ids)");
            })
            ->column('cate_id');
        $availableFlowCateIds = array_map('intval', $availableFlowCateIds);

        $modules = Db::name('FlowModule')
            ->where('status', 1)
            ->order('sort desc,id asc')
            ->select()
            ->toArray();
        foreach ($modules as &$module) {
            $list = Db::name('FlowCate')
                ->where([
                    ['module_id', '=', $module['id']],
                    ['status', '=', 1],
                    ['is_list', '=', 1],
                ])
                ->where(function ($query) use ($whereOr) {
                    if (!empty($whereOr)) {
                        $query->whereOr($whereOr);
                    }
                })
                ->order('sort desc,id asc')
                ->select()
                ->toArray();
            foreach ($list as &$item) {
                $item['mobile_supported'] = isset(self::MOBILE_APPLY_ROUTES[$item['name']])
                    && in_array((int) $item['id'], $availableFlowCateIds, true);
                if ($item['mobile_supported']) {
                    $item['add_url'] = self::MOBILE_APPLY_ROUTES[$item['name']];
                }
            }
            unset($item);
            $module['list'] = $list;
        }
        unset($module);

        View::assign([
            'page_title' => '审批申请',
            'approve_modules' => array_values(array_filter($modules, static function (array $module): bool {
                return !empty($module['list']);
            })),
        ]);
        return view();
    }

    public function add_qingjia()
    {
        return $this->renderAttendanceForm('leaves', '请假申请', 'add_qingjia');
    }

    public function add_chuchai()
    {
        return $this->renderAttendanceForm('trips', '出差申请', 'add_chuchai');
    }

    public function add_waichu()
    {
        return $this->renderAttendanceForm('outs', '外出申请', 'add_waichu');
    }

    public function add_jiaban()
    {
        return $this->renderAttendanceForm('overtimes', '加班申请', 'add_jiaban');
    }

    public function mylist()
    {
        if (request()->isAjax()) {
            $status = (int) request()->param('status', 0);
            $where = 'delete_time = 0 AND admin_id = ' . $this->uid;
            $where .= $this->statusWhere($status);
            return table_assign(0, '', $this->getList($where, get_params()));
        }

        return $this->renderList('我申请的', 'my');
    }

    public function checklist()
    {
        if (request()->isAjax()) {
            $status = (int) request()->param('status', 0);
            $uid = $this->uid;
            $where = 'delete_time = 0';
            if ($status === 1) {
                $where .= " AND FIND_IN_SET('{$uid}',check_uids)";
            } elseif ($status === 2) {
                $where .= " AND FIND_IN_SET('{$uid}',check_history_uids)";
            } else {
                $where .= " AND (FIND_IN_SET('{$uid}',check_uids) OR FIND_IN_SET('{$uid}',check_history_uids))";
            }
            return table_assign(0, '', $this->getList($where, get_params()));
        }

        return $this->renderList('我处理的', 'check');
    }

    public function copylist()
    {
        if (request()->isAjax()) {
            $status = (int) request()->param('status', 0);
            $where = "delete_time = 0 AND FIND_IN_SET('{$this->uid}',check_copy_uids)";
            $where .= $this->statusWhere($status);
            return table_assign(0, '', $this->getList($where, get_params()));
        }

        return $this->renderList('抄送给我', 'copy');
    }

    public function talentlist()
    {
        return $this->renderPersonnelList('入职管理', 'talent');
    }

    public function leavelist()
    {
        return $this->renderPersonnelList('离职管理', 'personal_quit');
    }

    public function changelist()
    {
        return $this->renderPersonnelList('人事调动', 'department_change');
    }

    public function detail()
    {
        $id = (int) request()->param('id', 0);
        $checkName = (string) request()->param('check_name', '');
        $tableName = (string) request()->param('table_name', '');
        $cate = Db::name('FlowCate')
            ->where(['name' => $checkName, 'check_table' => $tableName, 'status' => 1])
            ->find();
        if (
            empty($cate)
            || $id < 1
            || !preg_match('/^[a-z0-9_]+$/', $checkName)
            || !preg_match('/^[a-z0-9_]+$/', $tableName)
        ) {
            throw new \think\exception\HttpException(404, '找不到审批记录');
        }

        $query = Db::name($tableName)->where(['id' => $id, 'delete_time' => 0]);
        if ($this->uid > 1) {
            $uid = $this->uid;
            $query->where(function ($where) use ($uid) {
                $where->where('admin_id', $uid)
                    ->whereOrRaw("FIND_IN_SET('{$uid}',check_uids)")
                    ->whereOrRaw("FIND_IN_SET('{$uid}',check_history_uids)")
                    ->whereOrRaw("FIND_IN_SET('{$uid}',check_copy_uids)");
            });
        }
        $detail = $query->find();
        if (empty($detail)) {
            throw new \think\exception\HttpException(403, '无权查看该审批记录');
        }
        if ($tableName === 'seal') {
            $detail = $this->appendSealCategories($detail);
        }

        $fields = $this->detailFields($detail);
        View::assign([
            'page_title' => (string) $cate['title'],
            'approve_cate' => $cate,
            'detail' => $detail,
            'detail_fields' => $fields,
            'create_user' => get_admin((int) $detail['admin_id']),
        ]);
        return view();
    }

    private function renderPersonnelList(string $title, string $tableName)
    {
        if (request()->isAjax()) {
            $uid = $this->uid;
            $where = "delete_time = 0 AND (admin_id = {$uid}"
                . " OR FIND_IN_SET('{$uid}',check_uids)"
                . " OR FIND_IN_SET('{$uid}',check_history_uids)"
                . " OR FIND_IN_SET('{$uid}',check_copy_uids))";
            $where .= $this->statusWhere((int) request()->param('status', 0));
            return table_assign(0, '', $this->getList($where, get_params(), [$tableName]));
        }

        return $this->renderList($title, 'personnel');
    }

    private function renderList(string $title, string $mode)
    {
        View::assign([
            'page_title' => $title,
            'approve_list_mode' => $mode,
        ]);
        return view('list');
    }

    private function renderAttendanceForm(string $name, string $title, string $template)
    {
        $query = Db::name('FlowCate')->where([
            'name' => $name,
            'status' => 1,
            'is_list' => 1,
        ]);
        if ($this->uid > 1) {
            $did = $this->did;
            $query->where(function ($where) use ($did) {
                $where->where('department_ids', '')
                    ->whereOrRaw("FIND_IN_SET('{$did}',department_ids)");
            });
        }
        if (empty($query->find())) {
            throw new \think\exception\HttpException(403, '当前部门不能使用该审批类型');
        }

        View::assign('page_title', $title);
        return view($template);
    }

    private function statusWhere(int $status): string
    {
        if ($status === 1) {
            return ' AND check_status < 2';
        }
        if ($status === 2) {
            return ' AND check_status = 2';
        }
        if ($status === 3) {
            return ' AND check_status > 2';
        }
        return '';
    }

    private function getList(string $where, array $param, array $onlyTables = []): array
    {
        $tables = Db::name('FlowCate')
            ->field('name,check_table')
            ->where('status', 1)
            ->select()
            ->toArray();
        $prefix = (string) get_config('database.connections.mysql.prefix');
        $sqlParts = [];
        $sqlCounts = [];
        $usedTables = [];

        foreach ($tables as $table) {
            $dbName = (string) $table['check_table'];
            if (
                !preg_match('/^[a-z0-9_]+$/', $dbName)
                || in_array($dbName, $usedTables, true)
                || (!empty($onlyTables) && !in_array($dbName, $onlyTables, true))
            ) {
                continue;
            }

            $tableName = $prefix . $dbName;
            if (empty(Db::query("SHOW TABLES LIKE '{$tableName}'"))) {
                continue;
            }

            $checkName = (string) $table['name'];
            if (!preg_match('/^[a-z0-9_]+$/', $checkName)) {
                continue;
            }
            $select = "SELECT id,admin_id,did,create_time,check_status,check_flow_id,"
                . "check_step_sort,check_uids,check_last_uid,check_history_uids,"
                . "check_copy_uids,check_time,'{$dbName}' AS table_name,"
                . "'{$checkName}' AS check_name,'{$checkName}' AS invoice_type,"
                . "'{$checkName}' AS types FROM {$tableName} WHERE {$where}";
            if ($dbName === 'invoice' || $dbName === 'ticket') {
                $select = "SELECT id,admin_id,did,create_time,check_status,check_flow_id,"
                    . "check_step_sort,check_uids,check_last_uid,check_history_uids,"
                    . "check_copy_uids,check_time,'{$dbName}' AS table_name,"
                    . "'{$checkName}' AS check_name,invoice_type,'{$checkName}' AS types "
                    . "FROM {$tableName} WHERE {$where}";
            } elseif ($dbName === 'approve') {
                $select = "SELECT id,admin_id,did,create_time,check_status,check_flow_id,"
                    . "check_step_sort,check_uids,check_last_uid,check_history_uids,"
                    . "check_copy_uids,check_time,'{$dbName}' AS table_name,"
                    . "'{$checkName}' AS check_name,'{$checkName}' AS invoice_type,types "
                    . "FROM {$tableName} WHERE {$where}";
            }

            $sqlParts[] = $select;
            $sqlCounts[] = "SELECT COUNT(*) AS count FROM {$tableName} WHERE {$where}";
            $usedTables[] = $dbName;
        }

        if (empty($sqlParts)) {
            return ['data' => [], 'total' => 0];
        }

        $total = 0;
        foreach ($sqlCounts as $sql) {
            $total += (int) Db::query($sql)[0]['count'];
        }

        $page = max(1, (int) ($param['page'] ?? 1));
        $limit = min(50, max(1, (int) ($param['limit'] ?? $this->pageSize)));
        $offset = ($page - 1) * $limit;
        $result = Db::query(
            implode(' UNION ALL ', $sqlParts)
            . " ORDER BY create_time DESC LIMIT {$offset},{$limit}"
        );

        foreach ($result as &$row) {
            $row['create_time'] = to_date((int) $row['create_time'], 'Y-m-d H:i');
            $row['admin_name'] = Db::name('Admin')->where('id', $row['admin_id'])->value('name') ?: '-';
            $row['department'] = Db::name('Department')->where('id', $row['did'])->value('title') ?: '-';
            $row['check_status_str'] = check_status_name((int) $row['check_status']);
            $row['check_users'] = '-';
            if ((int) $row['check_status'] === 1 && !empty($row['check_uids'])) {
                $names = Db::name('Admin')->whereIn('id', $row['check_uids'])->column('name');
                $row['check_users'] = implode(',', $names);
            }

            $resolvedName = (string) $row['check_name'];
            if ($row['table_name'] === 'invoice' || $row['table_name'] === 'ticket') {
                $resolvedName = (int) $row['invoice_type'] === 0
                    ? $row['table_name'] . 'a'
                    : $row['table_name'];
            } elseif ($row['table_name'] === 'approve') {
                $resolvedName = 'approve_' . $row['types'];
            }
            $flowCate = Db::name('FlowCate')->where('name', $resolvedName)->find();
            $row['types_name'] = $flowCate['title'] ?? $resolvedName;
            $row['view_url'] = '/qiye/approve/detail?'
                . http_build_query([
                    'check_name' => $resolvedName,
                    'table_name' => $row['table_name'],
                    'id' => $row['id'],
                ]);
        }
        unset($row);

        return ['data' => $result, 'total' => $total];
    }

    private function detailFields(array $detail): array
    {
        $definitions = [
            'name' => '名称',
            'title' => '主题',
            'seal_cate' => '印章类型',
            'seal_num' => '盖章次数',
            'reason' => '事由',
            'content' => '内容',
            'remark' => '备注',
            'customer' => '客户',
            'supplier' => '供应商',
            'code' => '编号',
            'cost' => '金额',
            'duration' => '时长',
            'seal_use_time' => '预期用印日期',
            'seal_is_borrow' => '印章是否外借',
            'start_date' => '开始时间',
            'end_date' => '结束时间',
            'start_time' => '开始时间',
            'end_time' => '结束时间',
            'sign_time' => '签订时间',
            'quit_time' => '离职时间',
            'move_time' => '调动时间',
        ];
        $fields = [];
        foreach ($definitions as $key => $label) {
            if (
                !array_key_exists($key, $detail)
                || $detail[$key] === ''
                || ($detail[$key] === 0 && $key !== 'seal_is_borrow')
            ) {
                continue;
            }
            $value = $detail[$key];
            if (
                str_ends_with($key, '_time')
                || $key === 'start_date'
                || $key === 'end_date'
            ) {
                $value = to_date((int) $value, 'Y-m-d H:i');
            } elseif ($key === 'cost') {
                $value = '¥' . number_format((float) $value, 2);
            } elseif ($key === 'seal_is_borrow') {
                $value = (int) $value === 1 ? '是' : '否';
            }
            $fields[] = ['label' => $label, 'value' => strip_tags((string) $value)];
        }
        return $fields;
    }

    private function appendSealCategories(array $detail): array
    {
        $categories = Db::name('SealItem')
            ->alias('si')
            ->join('SealCate sc', 'sc.id = si.seal_cate_id', 'left')
            ->where('si.seal_id', (int) $detail['id'])
            ->field('si.seal_cate_id,sc.title')
            ->order('si.sort asc,si.id asc')
            ->select()
            ->toArray();
        if (empty($categories) && (int) ($detail['seal_cate_id'] ?? 0) > 0) {
            $title = Db::name('SealCate')
                ->where('id', (int) $detail['seal_cate_id'])
                ->value('title');
            $categories[] = ['title' => $title ?: ''];
        }
        $detail['seal_cate'] = implode(
            '、',
            array_values(array_filter(array_column($categories, 'title')))
        );
        $detail['seal_num'] = $detail['num'] ?? 0;
        $detail['seal_use_time'] = $detail['use_time'] ?? 0;
        $detail['seal_is_borrow'] = $detail['is_borrow'] ?? 0;
        return $detail;
    }
}
