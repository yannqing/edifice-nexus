<?php

declare(strict_types=1);

namespace app\qiye\controller;

use app\qiye\BaseController;

class Project extends BaseController
{
    public function index()
    {
        return redirect('/home/index/edifice_sso');
    }
}
