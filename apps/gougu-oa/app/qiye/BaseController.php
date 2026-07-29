<?php

declare(strict_types=1);

namespace app\qiye;

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

    public function __construct()
    {
        $this->controller = strtolower(Request::controller());
        $this->action = strtolower(Request::action());
        $this->checkLogin();
        $this->pageSize = (int) Request::param('limit', Config::get('app.page_size'));
        $this->assignMobileNavigation();
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
