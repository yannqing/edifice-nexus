<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\home\validate\UserCheck;
use think\exception\ValidateException;
use think\facade\Db;
use think\facade\Session;
use think\facade\View;

class Login
{
    public function index()
    {
        $sessionAdmin = get_config('app.session_admin');
        if (Session::has($sessionAdmin)) {
            return redirect('/qiye/index/index');
        }

        View::assign('page_title', '登录');
        return view();
    }

    public function login()
    {
        return $this->index();
    }

    public function login_submit()
    {
        $param = get_params();
        try {
            validate(UserCheck::class)->check($param);
        } catch (ValidateException $e) {
            return to_assign(1, $e->getError());
        }

        $admin = Db::name('Admin')
            ->where('delete_time', 0)
            ->where(function ($query) use ($param) {
                $query->where('username', $param['username'])->whereOr('mobile', $param['username']);
            })
            ->find();
        if (empty($admin)) {
            return to_assign(1, '用户名或手机号码错误');
        }

        $password = set_password($param['password'], $admin['salt']);
        if (!hash_equals((string) $admin['pwd'], $password)) {
            return to_assign(1, '用户或密码错误');
        }
        if ((int) $admin['status'] !== 1) {
            return to_assign(1, '该用户禁止登录，请联系管理员');
        }

        $now = time();
        Db::name('Admin')->where('id', $admin['id'])->update([
            'is_lock' => 0,
            'last_login_time' => $now,
            'last_login_ip' => request()->ip(),
            'login_num' => (int) $admin['login_num'] + 1,
        ]);
        Session::set(get_config('app.session_admin'), $admin['id']);
        Db::name('AdminLog')->strict(false)->field(true)->insert([
            'uid' => $admin['id'],
            'type' => 'login',
            'action' => '移动端登录',
            'subject' => '系统',
            'param_id' => $admin['id'],
            'param' => '[]',
            'ip' => request()->ip(),
            'create_time' => $now,
        ]);

        return to_assign(0, '登录成功', ['redirect' => '/qiye/index/index']);
    }

    public function login_out()
    {
        Session::delete(get_config('app.session_admin'));
        if (request()->isAjax()) {
            return to_assign(0, '退出成功', ['redirect' => '/qiye/login/index']);
        }

        return redirect('/qiye/login/index');
    }
}
