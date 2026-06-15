<?php

declare(strict_types=1);

namespace app\common\service;

class EdificeSync
{
    private const DEFAULT_BASE_URL = 'http://127.0.0.1:8081';

    public static function syncAdmin(int $adminId): array
    {
        if ($adminId <= 0) {
            return ['ok' => false, 'message' => 'OA 员工 ID 无效'];
        }
        return self::post('/admin/oa-sync/internal/users/' . $adminId);
    }

    public static function syncAllUsers(): array
    {
        return self::post('/admin/oa-sync/internal/users/full');
    }

    public static function getContractProjectStatus(int $contractId): array
    {
        return self::request('GET', '/admin/oa-sync/internal/contracts/' . $contractId . '/project');
    }

    public static function getProjectTypes(): array
    {
        return self::request('GET', '/admin/oa-sync/internal/contracts/project-types');
    }

    public static function createContractProject(int $contractId, array $payload): array
    {
        return self::request('POST', '/admin/oa-sync/internal/contracts/' . $contractId . '/project', $payload);
    }

    private static function post(string $path): array
    {
        return self::request('POST', $path);
    }

    private static function request(string $method, string $path, array $payload = []): array
    {
        $baseUrl = rtrim((string)env('EDIFICE_SYNC_BASE_URL', self::DEFAULT_BASE_URL), '/');
        $url = $baseUrl . $path;
        $headers = ['Content-Type: application/json'];
        $apiKey = (string)env('EDIFICE_SYNC_API_KEY', env('OA_SYNC_API_KEY', ''));
        if ($apiKey !== '') {
            $headers[] = 'X-OA-SYNC-KEY: ' . $apiKey;
        }

        if (!function_exists('curl_init')) {
            return ['ok' => false, 'message' => 'PHP curl 扩展不可用'];
        }

        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => 2,
            CURLOPT_TIMEOUT => 10,
        ]);
        if ($method !== 'GET') {
            curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload, JSON_UNESCAPED_UNICODE));
        }
        $body = curl_exec($ch);
        $errno = curl_errno($ch);
        $error = curl_error($ch);
        $status = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($errno !== 0) {
            error_log('[edifice-sync] request failed: ' . $error);
            return ['ok' => false, 'message' => $error ?: '请求 edifice 失败'];
        }
        if ($status < 200 || $status >= 300) {
            error_log('[edifice-sync] unexpected status ' . $status . ': ' . (string)$body);
            return ['ok' => false, 'message' => 'edifice 返回 HTTP ' . $status];
        }

        $json = json_decode((string)$body, true);
        if (!is_array($json)) {
            return ['ok' => false, 'message' => 'edifice 返回内容不是 JSON'];
        }
        if (isset($json['code']) && !in_array((int)$json['code'], [0, 200], true)) {
            return ['ok' => false, 'message' => (string)($json['msg'] ?? 'edifice 同步失败')];
        }
        return ['ok' => true, 'message' => (string)($json['msg'] ?? 'success'), 'data' => $json['data'] ?? null];
    }
}
