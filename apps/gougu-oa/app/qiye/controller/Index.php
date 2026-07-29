<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\qiye\BaseController;
use think\facade\Db;
use think\facade\View;

class Index extends BaseController
{
    public function index()
    {
        $notice = Db::name('Note')
            ->where(['status' => 1, 'delete_time' => 0])
            ->where('start_time', '<=', time())
            ->where('end_time', '>=', time())
            ->order('sort desc,id desc')
            ->find();
        View::assign([
            'page_title' => '工作台',
            'notice' => $notice,
        ]);
        return view();
    }

    public function calendar()
    {
        $rows = Db::name('Plan')
            ->where(['admin_id' => $this->uid, 'delete_time' => 0])
            ->order('start_time desc,id desc')
            ->limit(50)
            ->select()
            ->toArray();
        return $this->renderFeed('日程安排', $this->mapTimedRows($rows, 'remark'));
    }

    public function schedule()
    {
        $rows = Db::name('Schedule')
            ->where(['admin_id' => $this->uid, 'delete_time' => 0])
            ->order('start_time desc,id desc')
            ->limit(50)
            ->select()
            ->toArray();
        return $this->renderFeed('工作记录', $this->mapTimedRows($rows, 'remark'));
    }

    public function work()
    {
        $rows = Db::name('Work')
            ->where('delete_time', 0)
            ->where(function ($query) {
                $query->where('admin_id', $this->uid)
                    ->whereOrRaw("FIND_IN_SET('{$this->uid}',to_uids)");
            })
            ->order('create_time desc,id desc')
            ->limit(50)
            ->select()
            ->toArray();
        $items = [];
        $typeNames = [1 => '日报', 2 => '周报', 3 => '月报'];
        foreach ($rows as $row) {
            $items[] = [
                'title' => $typeNames[(int) $row['types']] ?? '工作汇报',
                'meta' => to_date((int) $row['create_time'], 'Y-m-d H:i'),
                'summary' => strip_tags((string) ($row['works'] ?: $row['plans'] ?: $row['remark'])),
            ];
        }
        return $this->renderFeed('工作汇报', $items);
    }

    public function note()
    {
        $rows = Db::name('Note')
            ->where(['status' => 1, 'delete_time' => 0])
            ->where('start_time', '<=', time())
            ->where('end_time', '>=', time())
            ->order('sort desc,id desc')
            ->limit(50)
            ->select()
            ->toArray();
        return $this->renderArticles('公告通知', $rows);
    }

    public function news()
    {
        $rows = Db::name('News')
            ->where('delete_time', 0)
            ->order('sort desc,id desc')
            ->limit(50)
            ->select()
            ->toArray();
        return $this->renderArticles('公司新闻', $rows);
    }

    public function meeting()
    {
        $rows = Db::name('MeetingRecords')
            ->where('delete_time', 0)
            ->where(function ($query) {
                $query->where('admin_id', $this->uid)
                    ->whereOrRaw("FIND_IN_SET('{$this->uid}',join_uids)")
                    ->whereOrRaw("FIND_IN_SET('{$this->uid}',share_uids)");
            })
            ->order('meeting_date desc,id desc')
            ->limit(50)
            ->select()
            ->toArray();
        $items = [];
        foreach ($rows as $row) {
            $items[] = [
                'title' => (string) $row['title'],
                'meta' => to_date((int) $row['meeting_date'], 'Y-m-d H:i'),
                'summary' => strip_tags((string) $row['content']),
            ];
        }
        return $this->renderFeed('会议纪要', $items);
    }

    public function admin()
    {
        $rows = Db::name('Admin')
            ->alias('a')
            ->leftJoin('Department d', 'd.id=a.did')
            ->leftJoin('Position p', 'p.id=a.position_id')
            ->where(['a.status' => 1, 'a.delete_time' => 0])
            ->field('a.id,a.name,a.mobile,a.thumb,d.title as department,p.title as position')
            ->order('a.name asc')
            ->select()
            ->toArray();
        View::assign([
            'page_title' => '企业人员',
            'employees' => $rows,
        ]);
        return view('employees');
    }

    private function renderArticles(string $title, array $rows)
    {
        $items = [];
        foreach ($rows as $row) {
            $items[] = [
                'title' => (string) $row['title'],
                'meta' => to_date((int) $row['create_time'], 'Y-m-d H:i'),
                'summary' => strip_tags((string) $row['content']),
            ];
        }
        return $this->renderFeed($title, $items);
    }

    private function mapTimedRows(array $rows, string $summaryField): array
    {
        $items = [];
        foreach ($rows as $row) {
            $items[] = [
                'title' => (string) $row['title'],
                'meta' => to_date((int) $row['start_time'], 'Y-m-d H:i')
                    . ' 至 '
                    . to_date((int) $row['end_time'], 'Y-m-d H:i'),
                'summary' => strip_tags((string) $row[$summaryField]),
            ];
        }
        return $items;
    }

    private function renderFeed(string $title, array $items)
    {
        View::assign([
            'page_title' => $title,
            'feed_items' => $items,
        ]);
        return view('feed');
    }
}
