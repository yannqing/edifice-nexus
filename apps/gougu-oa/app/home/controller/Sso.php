<?php
/**
 * Edifice -> Gougu OA single sign-on entry.
 */
declare(strict_types=1);

namespace app\home\controller;

use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use think\facade\Cache;
use think\facade\Db;
use think\facade\Session;

class Sso
{
    private const ISSUER = 'edifice-nexus';
    private const AUDIENCE = 'gougu-oa';
    public function login()
    {
        $token = request()->param('ssoToken') ?: request()->param('token');
        if (!$token) {
            return redirect('/home/login/index.html');
        }

        try {
            $secret = (string)env('EDIFICE_SSO_SECRET', '');
            if ($secret === '') {
                return redirect('/home/login/index.html');
            }
            $payload = (array) JWT::decode($token, new Key($secret, 'HS256'));
        } catch (\Throwable $e) {
            return redirect('/home/login/index.html');
        }

        if (($payload['iss'] ?? '') !== self::ISSUER || !$this->hasExpectedAudience($payload['aud'] ?? '')) {
            return redirect('/home/login/index.html');
        }

        $edificeUserId = trim((string) ($payload['userId'] ?? ''));
        if ($edificeUserId === '') {
            return redirect('/home/login/index.html');
        }

        $username = trim((string) ($payload['username'] ?? ''));
        if ($username === '') {
            $username = 'edifice_' . $edificeUserId;
        }
        $username = mb_substr($username, 0, 100);

        $realName = trim((string) ($payload['realName'] ?? ''));
        if ($realName === '') {
            $realName = $username;
        }
        $realName = mb_substr($realName, 0, 100);

        $email = mb_substr(trim((string) ($payload['email'] ?? '')), 0, 100);
        $phone = preg_replace('/\D+/', '', (string) ($payload['phone'] ?? ''));
        $mobile = $phone === '' ? 0 : (int) mb_substr($phone, 0, 11);
        $enabled = (int) ($payload['status'] ?? 1) === 1 ? 1 : 0;
        $now = time();

        $admin = Db::name('Admin')
            ->where(['userid' => $edificeUserId, 'delete_time' => 0])
            ->find();

        if (!$admin) {
            $admin = Db::name('Admin')
                ->where(['username' => $username, 'delete_time' => 0])
                ->find();
        }

        if (!$admin) {
            $salt = set_salt(20);
            $adminId = Db::name('Admin')->strict(false)->insertGetId([
                'userid' => $edificeUserId,
                'username' => $username,
                'pwd' => set_password(make_token(), $salt),
                'salt' => $salt,
                'name' => $realName,
                'nickname' => $realName,
                'email' => $email,
                'mobile' => $mobile,
                'did' => 1,
                'position_id' => 1,
                'sex' => 0,
                'type' => 1,
                'thumb' => '/static/home/images/icon.png',
                'entry_time' => $now,
                'create_time' => $now,
                'update_time' => $now,
                'last_login_time' => $now,
                'last_login_ip' => request()->ip(),
                'login_num' => 1,
                'auth_did' => 10,
                'status' => $enabled,
            ]);
        } else {
            $adminId = (int) $admin['id'];
            Db::name('Admin')->where('id', $adminId)->strict(false)->update([
                'userid' => $edificeUserId,
                'username' => $username,
                'name' => $realName,
                'nickname' => $realName,
                'email' => $email,
                'mobile' => $mobile,
                'did' => (int) ($admin['did'] ?? 0) > 0 ? (int) $admin['did'] : 1,
                'position_id' => (int) ($admin['position_id'] ?? 0) > 0 ? (int) $admin['position_id'] : 1,
                'status' => $enabled,
                'last_login_time' => $now,
                'last_login_ip' => request()->ip(),
                'login_num' => Db::raw('login_num + 1'),
                'update_time' => $now,
            ]);
        }

        if ($enabled !== 1) {
            return redirect('/home/login/index.html');
        }

        $sessionAdmin = get_config('app.session_admin');
        Session::set($sessionAdmin, $adminId);
        Cache::delete('menu' . $adminId);
        Cache::delete('RulesSrc' . $adminId);

        Db::name('AdminLog')->strict(false)->insert([
            'uid' => $adminId,
            'type' => 'login',
            'action' => 'SSO登录',
            'subject' => 'Edifice',
            'param_id' => $adminId,
            'param' => '[]',
            'ip' => request()->ip(),
            'create_time' => $now,
        ]);

        return redirect('/home/index/index.html');
    }

    private function hasExpectedAudience($audience): bool
    {
        if (is_array($audience)) {
            return in_array(self::AUDIENCE, $audience, true);
        }
        return (string) $audience === self::AUDIENCE;
    }
}
