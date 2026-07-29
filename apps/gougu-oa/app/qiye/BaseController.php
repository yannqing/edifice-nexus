<?php

declare(strict_types=1);

namespace app\qiye;

use think\exception\HttpException;
use think\facade\Config;
use think\facade\Db;
use think\facade\Request;
use think\facade\Session;
use think\facade\View;

abstract class BaseController
{
    protected int $uid = 0;
    protected int $did = 0;
    protected int $pid = 0;
    protected int $pageSize = 20;
    protected string $controller = '';
    protected string $action = '';
    protected array $mobileMenuTypes = [];
    protected array $mobileBars = [];
    protected array $mobileAllowedUrls = [];

    public function __construct()
    {
        $this->controller = strtolower(Request::controller());
        $this->action = strtolower(Request::action());
        $this->checkLogin();
        $this->pageSize = (int) Request::param('limit', Config::get('app.page_size'));
        $this->assignMobileNavigation();
        $this->checkMobilePermission();
    }

    private function checkLogin(): void
    {
        $sessionAdmin = get_config('app.session_admin');
        if (!Session::has($sessionAdmin)) {
            $this->redirectToLogin();
        }

        $this->uid = (int) Session::get($sessionAdmin);
        $loginAdmin = get_admin($this->uid);
        if (
            empty($loginAdmin)
            || (int) $loginAdmin['status'] !== 1
            || (int) $loginAdmin['delete_time'] > 0
        ) {
            Session::delete($sessionAdmin);
            $this->redirectToLogin();
        }

        $lastLoginTime = (int) $loginAdmin['last_login_time'];
        if ($lastLoginTime > 0 && time() - $lastLoginTime > 36000) {
            Session::delete($sessionAdmin);
            $this->redirectToLogin();
        }

        $this->did = (int) $loginAdmin['did'];
        $this->pid = (int) $loginAdmin['pid'];
        Db::name('Admin')->where('id', $this->uid)->update(['last_login_time' => time()]);
        View::assign('login_admin', $loginAdmin);
    }

    private function redirectToLogin(): void
    {
        if (request()->isAjax()) {
            to_assign(401, '请先登录', ['redirect' => '/qiye/login/index']);
        }

        redirect('/qiye/login/index')->send();
        exit;
    }

    private function assignMobileNavigation(): void
    {
        $menuIds = [];
        $barIds = [];

        if ($this->uid === 1) {
            $menuIds = Db::name('MobileMenu')->where('status', 1)->column('id');
            $barIds = Db::name('MobileBar')->where('status', 1)->column('id');
        } else {
            $positionId = (int) Db::name('Admin')->where('id', $this->uid)->value('position_id');
            $groupIds = Db::name('PositionGroup')->where('pid', $positionId)->column('group_id');
            if (!empty($groupIds)) {
                $groups = Db::name('AdminGroup')
                    ->where('status', 1)
                    ->whereIn('id', $groupIds)
                    ->field('mobile_menu,mobile_bar')
                    ->select()
                    ->toArray();
                foreach ($groups as $group) {
                    $menuIds = array_merge($menuIds, $this->csvIds((string) $group['mobile_menu']));
                    $barIds = array_merge($barIds, $this->csvIds((string) $group['mobile_bar']));
                }
            }
        }

        $menuIds = array_values(array_unique(array_map('intval', $menuIds)));
        $barIds = array_values(array_unique(array_map('intval', $barIds)));
        if (empty($barIds)) {
            $barIds = [1];
        }

        $bars = Db::name('MobileBar')
            ->where('status', 1)
            ->whereIn('id', $barIds)
            ->order('sort desc,id asc')
            ->select()
            ->toArray();
        $this->mobileBars = array_values(array_filter($bars, static function (array $bar): bool {
            return !str_starts_with((string) $bar['url'], '/qiye/project');
        }));
        foreach ($this->mobileBars as $bar) {
            $this->addMobileAllowedUrl((string) $bar['url']);
        }
        $currentPath = '/qiye/' . $this->controller;
        foreach ($this->mobileBars as &$bar) {
            $bar['active'] = str_starts_with($currentPath, dirname((string) $bar['url']));
        }
        unset($bar);

        $types = Db::name('MobileTypes')
            ->where('status', 1)
            ->order('sort desc,id asc')
            ->select()
            ->toArray();
        foreach ($types as $type) {
            $menus = [];
            if (!empty($menuIds)) {
                $menus = Db::name('MobileMenu')
                    ->where('status', 1)
                    ->where('types', (int) $type['id'])
                    ->whereIn('id', $menuIds)
                    ->order('sort desc,id asc')
                    ->select()
                    ->toArray();
            }
            $menus = array_values(array_filter($menus, static function (array $menu): bool {
                return !str_starts_with((string) $menu['url'], '/qiye/project');
            }));
            if (!empty($menus)) {
                foreach ($menus as $menu) {
                    $this->addMobileAllowedUrl((string) $menu['url']);
                }
                $type['list'] = $menus;
                $this->mobileMenuTypes[] = $type;
            }
        }

        $unreadCount = Db::name('Msg')
            ->where(['to_uid' => $this->uid, 'read_time' => 0, 'delete_time' => 0])
            ->count();
        View::assign([
            'mobile_bars' => $this->mobileBars,
            'mobile_menu_types' => $this->mobileMenuTypes,
            'mobile_current_path' => $currentPath,
            'mobile_unread_count' => $unreadCount,
        ]);
    }

    private function checkMobilePermission(): void
    {
        $requiredUrls = $this->requiredMobilePermissionUrls();
        if ($requiredUrls === []) {
            return;
        }

        if ($requiredUrls !== null) {
            foreach ($requiredUrls as $url) {
                if ($this->hasMobilePermission($url)) {
                    return;
                }
            }
        }

        if (request()->isAjax()) {
            to_assign(403, '无权访问该移动端功能', [], '', '', 403);
        }
        throw new HttpException(403, '无权访问该移动端功能');
    }

    private function requiredMobilePermissionUrls(): ?array
    {
        $route = $this->controller . '/' . $this->action;
        if ($route === 'index/index') {
            return [];
        }

        $controllerPermissions = [
            'customer' => '/qiye/customer/index',
            'contract' => '/qiye/contract/index',
            'msg' => '/qiye/msg/index',
            'project' => '/qiye/project/index',
        ];
        if (isset($controllerPermissions[$this->controller])) {
            return [$controllerPermissions[$this->controller]];
        }

        if ($route === 'finance/view') {
            $type = (string) Request::param('type', '');
            $financeUrls = [
                'expense' => '/qiye/finance/expense',
                'invoice' => '/qiye/finance/invoice',
                'ticket' => '/qiye/finance/ticket',
                'income' => '/qiye/finance/income',
                'payment' => '/qiye/finance/payment',
                'loan' => '/qiye/finance/loan',
            ];
            return isset($financeUrls[$type]) ? [$financeUrls[$type]] : null;
        }

        if ($route === 'approve/detail') {
            $checkName = (string) Request::param('check_name', '');
            $personnelUrls = [
                'talent' => '/qiye/approve/talentlist',
                'personal_quit' => '/qiye/approve/leavelist',
                'department_change' => '/qiye/approve/changelist',
            ];
            if (isset($personnelUrls[$checkName])) {
                return [$personnelUrls[$checkName]];
            }
            return [
                '/qiye/approve/apply',
                '/qiye/approve/mylist',
                '/qiye/approve/checklist',
                '/qiye/approve/copylist',
            ];
        }

        $actionPermissions = [
            'index/calendar' => '/qiye/index/calendar',
            'index/schedule' => '/qiye/index/schedule',
            'index/work' => '/qiye/index/work',
            'index/note' => '/qiye/index/note',
            'index/news' => '/qiye/index/news',
            'index/meeting' => '/qiye/index/meeting',
            'index/admin' => '/qiye/index/admin',
            'approve/apply' => '/qiye/approve/apply',
            'approve/add_qingjia' => '/qiye/approve/apply',
            'approve/add_chuchai' => '/qiye/approve/apply',
            'approve/add_waichu' => '/qiye/approve/apply',
            'approve/add_jiaban' => '/qiye/approve/apply',
            'approve/mylist' => '/qiye/approve/mylist',
            'approve/checklist' => '/qiye/approve/checklist',
            'approve/copylist' => '/qiye/approve/copylist',
            'approve/talentlist' => '/qiye/approve/talentlist',
            'approve/leavelist' => '/qiye/approve/leavelist',
            'approve/changelist' => '/qiye/approve/changelist',
            'finance/expense' => '/qiye/finance/expense',
            'finance/invoice' => '/qiye/finance/invoice',
            'finance/ticket' => '/qiye/finance/ticket',
            'finance/income' => '/qiye/finance/income',
            'finance/payment' => '/qiye/finance/payment',
            'finance/loan' => '/qiye/finance/loan',
        ];

        return isset($actionPermissions[$route]) ? [$actionPermissions[$route]] : null;
    }

    protected function hasMobilePermission(string $url): bool
    {
        return in_array($this->normalizeMobileUrl($url), $this->mobileAllowedUrls, true);
    }

    private function addMobileAllowedUrl(string $url): void
    {
        $normalized = $this->normalizeMobileUrl($url);
        if ($normalized !== '' && !in_array($normalized, $this->mobileAllowedUrls, true)) {
            $this->mobileAllowedUrls[] = $normalized;
        }
    }

    private function normalizeMobileUrl(string $url): string
    {
        $path = parse_url($url, PHP_URL_PATH);
        if (!is_string($path) || $path === '') {
            return '';
        }
        return '/' . trim($path, '/');
    }

    private function csvIds(string $value): array
    {
        if ($value === '') {
            return [];
        }

        return array_values(array_filter(array_map('intval', explode(',', $value))));
    }

    protected function paginationParams(): array
    {
        return [
            'page' => max(1, (int) Request::param('page', 1)),
            'limit' => min(50, max(1, (int) Request::param('limit', $this->pageSize))),
        ];
    }
}
